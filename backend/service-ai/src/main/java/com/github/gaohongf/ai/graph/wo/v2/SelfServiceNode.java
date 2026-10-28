package com.github.gaohongf.ai.graph.wo.v2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.entity.dto.KnowledgeHit;
import com.github.gaohongf.ai.entity.dto.KnowledgeSource;
import com.github.gaohongf.ai.entity.dto.SelfServicePlan;
import com.github.gaohongf.ai.entity.dto.WorkOrderProblemDraft;

import lombok.extern.slf4j.Slf4j;

/**
 * 判断知识库片段能不能解决用户的问题，并整理出可以照着做的步骤。
 *
 * <p>
 * 它只产出"是否可用"和一段文字，不直接输出给用户；真正的输出在
 * {@link SolutionReplyNode}，决策卡片在 {@link DecisionOptionsNode}。
 * 这样"判不可用"时不会先给用户看到半截建议再撤回。
 */
@Slf4j
public class SelfServiceNode extends AINode {

    /** 单个片段的截断长度：5 个片段已经足够，太长只会稀释重点 */
    private static final int MAX_HIT_CHARS = 1200;
    private static final String NO_CONTENT = "（无）";

    /** 正文里的《文章标题》，出处兜底时从"参考"行里取 */
    private static final Pattern BOOK_TITLE = Pattern.compile("《([^》]+)》");

    private static final String SYSTEM_PROMPT = """
            你是运维工单系统的自助解决助手。
            你的职责：判断给出的知识库片段能否直接解决用户的问题。能，就给出用户自己可以照着做的解决步骤；不能，就明确返回不可用。

            规则：
            1. 只能依据给出的知识库片段，不允许编造片段中没有的命令、地址、账号或操作步骤。
            2. 片段与问题无关、只沾一点边、或只描述现象而没有处理方法时，usable=false，solution 留空。
               宁可判不可用，也不要把不相关的内容硬凑成答案。
            3. 能解决时，solution 写成 2-6 个短步骤，每步一行，用换行分隔；
               语言要口语化，让不懂技术的人也能照着做。
            4. intro 是引出步骤的一句口语化的话，像同事顺口说的：
               "可以先按下面这几步试试""建议你先按下面处理一下，看能不能恢复"。
               按问题调整语气，别每句都一样；不要写"以下是解决方法"这类套话，
               不要在 intro 里预告具体步骤（步骤在 solution 里）。
            5. 涉及重启、断电、删除、重装等有风险的操作时，先用一行"注意：……"提醒风险，再写步骤。
               这一行是提醒，不是步骤，不要给它编号。
            6. sources 字段列出这次答复用到的文章标题：从片段开头的《》里原样复制，只写标题，
               不要带书名号、也不要带"第 N 段"；没用到的片段不要列。
               正文（intro、solution）里不要写"参考""出处"之类的一行，也不要写"根据知识库"这类开场白。
            7. 不要输出 Markdown 表格、代码块或编号列表（不要用 1. 2.），用自然语言短句，每句一行。
            8. 如果片段明确要求联系管理员或供应商，说明知识库解决不了，usable=false。
            9. 时间、地点只用于安排人上门，与能不能自助解决无关；标为"未提供"时照常判断，
               不要因为它们缺失就判为不可用。
            """;

    private static final String USER_PROMPT = """
            用户的问题：
            {problem}

            用户补充的时间：{time}
            用户补充的地点：{location}

            知识库片段：
            {hits}
            """;

    public SelfServiceNode(ChatClient chatClient) {
        super(chatClient);
        this.systemPrompt = SYSTEM_PROMPT;
        this.userPromptTemplate = USER_PROMPT;
    }

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        List<KnowledgeHit> hits = readHits(state);
        if (hits.isEmpty()) {
            return unavailable();
        }

        WorkOrderProblemDraft draft = state.value("draft", WorkOrderProblemDraft.class).orElse(null);
        String problem = problemOf(state, draft);
        String time = draft == null || !StringUtils.hasText(draft.getUserTimeDescription())
                ? "未提供"
                : draft.getUserTimeDescription().trim();
        String location = draft == null || !StringUtils.hasText(draft.getUserLocationDescription())
                ? "未提供"
                : draft.getUserLocationDescription().trim();

        SelfServicePlan plan;
        try {
            plan = prompt(Map.of(
                    "problem", problem,
                    "time", time,
                    "location", location,
                    "hits", formatHits(hits)))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(state)))
                    .call()
                    .entity(SelfServicePlan.class);
        } catch (RuntimeException ex) {
            // 模型这一步失败不应该让整段对话失败：跳过自助解决，继续走工单
            log.warn("自助解决建议生成失败，转工单流程: {}", ex.getMessage());
            return unavailable();
        }

        if (plan == null || !Boolean.TRUE.equals(plan.getUsable())
                || !StringUtils.hasText(plan.getSolution())) {
            return unavailable();
        }

        String solution = plan.getSolution().trim();
        // 出处不再拼进正文：前端要拿 id 做成可点击的链接，拼成文字就没有 id 了
        List<KnowledgeSource> sources = resolveSources(plan.getSources(), hits);
        if (sources.isEmpty()) {
            // 模型偶尔仍按旧习惯把出处写成正文的一行；这里捞出来，否则前端整行消失
            sources = citedSources(solution, hits);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("selfServiceUsable", true);
        output.put("selfServiceSolution", solution);
        // 引导语可空：模型没给就不说，前端只显示步骤列表
        output.put("selfServiceIntro", plan.getIntro() == null ? "" : plan.getIntro().trim());
        output.put("selfServiceSources", sources);
        return output;
    }

    private static Map<String, Object> unavailable() {
        Map<String, Object> output = new HashMap<>();
        output.put("selfServiceUsable", false);
        output.put("selfServiceSolution", "");
        output.put("selfServiceIntro", "");
        output.put("selfServiceSources", List.of());
        // 自助解决不了，只能人工：从这里开始才需要时间、地点
        output.put("phase", WorkflowPhase.WORK_ORDER);
        return output;
    }

    private static List<KnowledgeHit> readHits(OverAllState state) {
        return state.value("knowledgeHits", List.class)
                .map(items -> items.stream()
                        .filter(KnowledgeHit.class::isInstance)
                        .map(KnowledgeHit.class::cast)
                        .toList())
                .orElse(List.of());
    }

    private static String problemOf(OverAllState state, WorkOrderProblemDraft draft) {
        if (draft != null && StringUtils.hasText(draft.getUserMessages())) {
            return draft.getUserMessages().trim();
        }
        return state.value("userInput", String.class).orElse("（用户没有提供更多描述）");
    }

    private static String formatHits(List<KnowledgeHit> hits) {
        return hits.stream()
                .map(hit -> {
                    String content = hit.getContent().trim();
                    if (content.length() > MAX_HIT_CHARS) {
                        content = content.substring(0, MAX_HIT_CHARS);
                    }
                    String title = StringUtils.hasText(hit.getTitle()) ? hit.getTitle() : "未命名文章";
                    return "《" + title + "》第 " + hit.getChunkIndex() + " 段\n" + content;
                })
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 把模型给出的文章标题对回检索结果，拿到可跳转的文章 id。
     *
     * <p>
     * 模型只会复述标题，还可能带上书名号，所以先归一化再比；对不上也保留标题
     * （articleId 为 null），界面照样能显示出处，只是点不开。
     */
    private static List<KnowledgeSource> resolveSources(List<String> titles, List<KnowledgeHit> hits) {
        if (titles == null || titles.isEmpty()) {
            return List.of();
        }
        // 不用 Collectors.toMap：它有 null 值时直接抛 NPE，而 id 恰好允许为空
        Map<String, String> idByTitle = new HashMap<>();
        for (KnowledgeHit hit : hits) {
            if (StringUtils.hasText(hit.getTitle())) {
                idByTitle.putIfAbsent(normalizeTitle(hit.getTitle()), hit.getArticleId());
            }
        }

        List<KnowledgeSource> sources = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : titles) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String title = normalizeTitle(raw);
            if (title.isEmpty() || !seen.add(title)) {
                continue;
            }
            sources.add(new KnowledgeSource(findArticleId(title, idByTitle), title));
        }
        return sources;
    }

    /**
     * 把标题对回检索结果里的文章 id。
     *
     * <p>
     * 先按归一化后的标题精确匹配；模型偶尔会多带一截（比如"××排查（Windows）"），
     * 再退一步做包含匹配 —— 只要能唯一对上，就让它可点开。
     */
    private static String findArticleId(String title, Map<String, String> idByTitle) {
        String exact = idByTitle.get(title);
        if (exact != null) {
            return exact;
        }
        String matched = null;
        for (Map.Entry<String, String> entry : idByTitle.entrySet()) {
            String candidate = entry.getKey();
            if (candidate.isEmpty() || !(candidate.contains(title) || title.contains(candidate))) {
                continue;
            }
            if (matched != null) {
                // 能对上不止一篇时宁可不跳：跳到错的文章比点不开更糟
                return null;
            }
            matched = entry.getValue();
        }
        return matched;
    }

    /**
     * 模型的出处写进了正文（旧提示词的写法）：从"参考"行里把标题捞出来。
     *
     * <p>
     * 提示词已改成填 sources 字段，这里只是兜底 —— 模型没照做时，
     * 前端至少要还能看到出处，而不是整行消失。
     */
    private static List<KnowledgeSource> citedSources(String solution, List<KnowledgeHit> hits) {
        List<String> titles = new ArrayList<>();
        for (String line : solution.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("参考")) {
                continue;
            }
            Matcher matcher = BOOK_TITLE.matcher(trimmed);
            while (matcher.find()) {
                titles.add(matcher.group(1));
            }
            if (!titles.isEmpty()) {
                continue;
            }
            // 没带书名号时取冒号后面的部分，多篇用顿号分隔
            int colon = trimmed.indexOf('：');
            if (colon < 0) {
                colon = trimmed.indexOf(':');
            }
            if (colon >= 0) {
                for (String title : trimmed.substring(colon + 1).split("[、,，]")) {
                    titles.add(title);
                }
            }
        }
        return resolveSources(titles, hits);
    }

    /**
     * 去掉标题上已有的书名号。
     *
     * <p>
     * 提示词示例里写的是"参考：《文章标题》"，模型经常照抄，把书名号一起塞进
     * sources，直接拼就变成《《标题》》。这里只负责去掉外层符号，标题本身的
     * 内容不改。
     */
    private static String stripBookMarks(String title) {
        String normalized = title.trim();
        while (normalized.startsWith("《")) {
            normalized = normalized.substring("《".length()).trim();
        }
        while (normalized.endsWith("》")) {
            normalized = normalized.substring(0, normalized.length() - "》".length()).trim();
        }
        return normalized;
    }

    /**
     * 标题归一化后再比对。
     *
     * <p>
     * 除了书名号，模型还可能把片段标题整个抄过来 —— "《××排查》第 2 段"，
     * 末尾那截"第 N 段"要一起去掉，否则永远对不上原文。
     */
    private static String normalizeTitle(String title) {
        return stripBookMarks(title).replaceAll("第\\s*\\d+\\s*段$", "").trim();
    }
}
