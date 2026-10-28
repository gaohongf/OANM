package com.github.gaohongf.ai.graph.wo.v2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.config.KnowledgeProperties;
import com.github.gaohongf.ai.entity.dto.KnowledgeHit;
import com.github.gaohongf.ai.entity.dto.KnowledgeProbe;
import com.github.gaohongf.ai.entity.dto.ProbeItem;
import com.github.gaohongf.ai.entity.dto.WorkOrderProblemDraft;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 用知识库里命中的文章确认"你遇到的是哪一种故障"。
 *
 * <h2>为什么一次问多篇</h2>
 * 知识库常常对同一类故障存了多篇针对不同情况的文章（卡纸 / 打印没反应）。只挑一篇问
 * "是不是卡纸"，用户遇到的是另一篇时，那条知识就永远没机会被用上。所以这里把每篇
 * 文章各自的现象并列问出来（"是纸卡住了，还是点打印没反应？"），用户点哪一项，
 * 下一轮就用那句话重新检索，自然落到对应的文章上。
 *
 * <p>
 * 上限是三篇：再多，问题会变成一长串选项，用户读不下去，也不比不问好。
 *
 * <h2>为什么单独占一轮，不和追问合在一起</h2>
 * 把"是卡纸还是没反应"和"什么时候、在哪里"写进同一条回复，用户往往只挑时间地点回答，
 * 确认就被跳过了，流程直接落到工单选项上。拆成两轮：这一轮只确认故障，
 * 用户点卡片或在输入框里回一句都行；确认完再问缺少的信息。
 *
 * <p>
 * 判断的是"值不值得问"，不是"用户是不是已经说了这个故障"：用户只报了一句
 * "打印机坏了"时，具体现象本来就还没说出口 —— 那正是这一轮要问的东西。
 * 只有文章讲的是完全不同的设备，或用户已经明确描述了另一种故障时才不出声，
 * 条件边直接转到追问。问过一次就不再问（{@code probeAsked}），用户不理会时也不会反复纠缠。
 */
@Slf4j
public class KnowledgeProbeNode extends AINode {

    /** 单篇文章给模型看的长度：判断相关性够用，又不至于把整篇塞进提示词 */
    private static final int MAX_ARTICLE_CHARS = 800;

    /** 一次最多问几篇文章。见类注释：超过三篇，选项会多到没人愿意读 */
    private static final int MAX_ARTICLES = 3;

    /**
     * 句首自报出处的老写法："我在知识库里看到一篇《……》，……"。
     *
     * <p>
     * 提示词已经明确不这么写，但历史对话里全是这种句子，模型会照着仿；
     * 用户要的是直接提问，所以这里再把这一截砍掉，砍完只剩出处就退回原文。
     */
    private static final Pattern LEAD_IN = Pattern.compile(
            "^(?:我)?(?:在|从)知识库(?:里|中)?(?:看到|找到|查到|检索到)(?:了)?[^，,：:。？?]{0,24}[，,：:]\\s*");

    private static final String SYSTEM_PROMPT = """
            你是运维工单系统的自助诊断助手。
            你的职责：在知识库命中的文章里挑出可能就是用户遇到的问题的那几篇，
            把它们各自对应的现象并列问出来，让用户指出是哪一种。
            用户此时通常只说了很笼统的一句话，具体现象正是这一轮要问出来的。

            规则：
            1. matched 表示"值得问一句"，不是"用户已经说出了同样的故障"。
               - 用户只说了对象和"坏了 / 用不了 / 有问题 / 不太对"这类笼统描述时，
                 只要文章讲的是同一类设备或系统（打印机对打印机、网络对网络），
                 并且文章里的故障是可能的原因之一，matched 就为 true。
                 用户还没说具体现象，这正是需要提问的原因；不能因为"没提到"就判 false。
               - 只有两种情况判 false：文章讲的是完全不同的设备或系统
                 （用户说打印机、文章讲空调），或者用户已经明确描述了另一种故障
                 （用户说打出来有黑条、所有文章都在讲卡纸和打印没反应）。
               - 拿不准时倾向 true：把常见的可能原因问出来，用户否认一次也没有损失。
            2. matched 为 true 时，items 每一项对应一篇文章：
               - article：从给的文章标题里原样复制，不要带书名号；
                 一篇文章只能出现一次，最多 3 项。
               - answer：用户确认这种情况时的一句口语，要带上具体现象，
                 例如"是打印机卡纸了""是点了打印没反应"。
                 各项之间要能明显区分，不能是同一句话的变体。
            3. question：一句话，把 items 里的现象并列问出来，例如
               "你说的打印机坏了，是纸卡住了，还是点打印没反应？"。
               不要出现"知识库""文章""我这边看到"之类的说法，也不要出现文章标题或书名号；
               只问现象：不要问时间、地点，也不要给出任何操作步骤。
            4. matched 为 false 时，question 返回空字符串，items 返回空数组。
            5. 不要编造文章里没有的故障，也不要把同一篇文章拆成几项。
            """;

    private static final String USER_PROMPT = """
            用户的问题：
            {problem}

            知识库中命中的文章：
            {articles}
            """;

    private final KnowledgeProperties properties;

    public KnowledgeProbeNode(ChatClient chatClient, KnowledgeProperties properties) {
        super(chatClient);
        this.systemPrompt = SYSTEM_PROMPT;
        this.userPromptTemplate = USER_PROMPT;
        this.properties = properties;
    }

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        List<KnowledgeHit> hits = probeHits(state);
        if (hits.isEmpty()) {
            return skipped();
        }
        WorkOrderProblemDraft draft = state.value("draft", WorkOrderProblemDraft.class).orElse(null);
        String problem = draft == null || !StringUtils.hasText(draft.getUserMessages())
                ? state.value("userInput", String.class).orElse("（用户没有提供更多描述）")
                : draft.getUserMessages().trim();
        KnowledgeProbe probe;
        try {
            probe = prompt(Map.of("problem", problem, "articles", formatArticles(hits)))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(state)))
                    .call()
                    .entity(KnowledgeProbe.class);
        } catch (RuntimeException ex) {
            // 判断失败不阻断流程：安静地转去追问时间地点
            log.warn("知识库故障确认生成失败，转追问: {}", ex.getMessage());
            return skipped();
        }

        if (probe == null || !probe.isMatched()) {
            return skipped();
        }
        List<String> answers = answersOf(probe.getItems());
        if (answers.isEmpty()) {
            // 没有一张卡片就没法确认，问了也白问
            return skipped();
        }

        String question = stripLeadIn(probe.getQuestion());
        if (!StringUtils.hasText(question)) {
            question = "你说的情况，更接近下面哪一种？";
        }

        Map<String, Object> output = new HashMap<>();
        output.put("probeAsked", true);
        output.put("probeMatched", true);
        output.put("probeAnswers", answers);
        // 这一轮的可见输出：一句提问（前端按换行拆气泡，这里就一句）
        output.put("messages", Flux.just(question));
        return output;
    }

    /**
     * 把模型给的候选现象整理成卡片文字。
     *
     * <p>
     * 同一篇文章只留第一项（模型偶尔把一篇拆成两项），同一句话也只留一项
     * （两篇文章的现象说法撞车时，用户在卡片上分不出区别）；空 answer 直接丢掉。
     * 上限与 {@link #MAX_ARTICLES} 一致 —— 卡片比文章多没有意义。
     */
    private static List<String> answersOf(List<ProbeItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<String> answers = new ArrayList<>();
        Set<String> seenArticles = new HashSet<>();
        Set<String> seenAnswers = new HashSet<>();
        for (ProbeItem item : items) {
            if (item == null || !StringUtils.hasText(item.getAnswer())) {
                continue;
            }
            String answer = item.getAnswer().trim();
            String article = normalizeTitle(item.getArticle());
            if (!seenAnswers.add(answer) || (!article.isEmpty() && !seenArticles.add(article))) {
                continue;
            }
            answers.add(answer);
            if (answers.size() >= MAX_ARTICLES) {
                break;
            }
        }
        return answers;
    }

    /** 已问过就不再问，标记置真，下次直接走追问 */
    private static Map<String, Object> skipped() {
        return Map.of("probeAsked", true, "probeMatched", false);
    }

    /**
     * 参与提问的文章：每篇只留分数最高的那段。
     *
     * <p>
     * 检索按片段返回，同一篇文章常常命中好几段。不按文章去重的话，
     * "最多三篇"会被同一篇文章的几段占满，其余文章又白命中了。
     */
    private List<KnowledgeHit> probeHits(OverAllState state) {
        List<?> raw = state.value("knowledgeHits", List.class).orElse(List.of());
        Map<String, KnowledgeHit> bestByArticle = new LinkedHashMap<>();
        for (Object item : raw) {
            if (!(item instanceof KnowledgeHit hit)
                    || hit.getScore() < properties.getProbeMinScore()
                    || !StringUtils.hasText(hit.getContent())) {
                continue;
            }
            bestByArticle.merge(articleKey(hit), hit,
                    (left, right) -> left.getScore() >= right.getScore() ? left : right);
        }
        return bestByArticle.values().stream()
                .sorted(Comparator.comparingDouble(KnowledgeHit::getScore).reversed())
                .limit(MAX_ARTICLES)
                .toList();
    }

    /** 去重用的文章标识：id 优先，退到标题；都没有就按片段各自算一篇 */
    private static String articleKey(KnowledgeHit hit) {
        if (StringUtils.hasText(hit.getArticleId())) {
            return hit.getArticleId();
        }
        if (StringUtils.hasText(hit.getTitle())) {
            return hit.getTitle().trim();
        }
        return "chunk-" + hit.getChunkIndex();
    }

    private static String formatArticles(List<KnowledgeHit> hits) {
        return hits.stream()
                .map(hit -> {
                    String content = hit.getContent().trim();
                    if (content.length() > MAX_ARTICLE_CHARS) {
                        content = content.substring(0, MAX_ARTICLE_CHARS);
                    }
                    String title = StringUtils.hasText(hit.getTitle()) ? hit.getTitle() : "未命名文章";
                    return "《" + title + "》\n" + content;
                })
                .collect(Collectors.joining("\n\n"));
    }

    /** 砍掉句首的"我在知识库里看到……"，见 {@link #LEAD_IN} */
    private static String stripLeadIn(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        String trimmed = question.trim();
        String stripped = LEAD_IN.matcher(trimmed).replaceFirst("").trim();
        return stripped.isEmpty() ? trimmed : stripped;
    }

    /** 去掉书名号：模型常照着示例把《》一起抄进 article */
    private static String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        String normalized = title.trim();
        while (normalized.startsWith("《")) {
            normalized = normalized.substring("《".length()).trim();
        }
        while (normalized.endsWith("》")) {
            normalized = normalized.substring(0, normalized.length() - "》".length()).trim();
        }
        return normalized;
    }
}
