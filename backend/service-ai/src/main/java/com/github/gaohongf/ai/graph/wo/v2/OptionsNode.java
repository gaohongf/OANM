package com.github.gaohongf.ai.graph.wo.v2;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.entity.dto.IntentOption;
import com.github.gaohongf.ai.entity.dto.WorkOrderProblemDraft;
import com.github.gaohongf.ai.util.JsonLinesAIOutputHandler;
import com.github.gaohongf.ai.util.JsonLinesOutputConverter;

import reactor.core.publisher.Flux;

/**
 * 信息齐全后给出 3-5 个工单意图，供用户选择。
 *
 * <p>
 * 它同时承担"自助解决失败后的出口"：如果用户刚看过一份不奏效的自助方案，
 * 提示词会要求模型不要再重复同样的建议，而是给出需要人工处理的意图。
 */
public class OptionsNode extends AIStreamOutputNode<IntentOption> {

    private static final String USER_PROMPT = """
            你是一个运维工单系统的意图分析助手。
            用户之前提交了故障描述或一些需求，但往往描述不清。

            你在当前节点的任务是:
            1. 区分用户输入是需求还是故障，推测 3-5 个具体的用户意图作为选项，供用户选择确认。
            2. 每个选项代入用户的视角描述"我想要什么"，不要写你自己的回应。严禁出现"我无法""我不能"
               "请提供更多信息""建议联系供应商"这类能力声明、免责说明或指路建议 —— 它们不是意图。
            3. **选项里不允许出现任何提问**。能走到这一步，就说明时间和地点都已经有了；
               再写"请告诉我时间""请补充地点"之类的选项是错的。
            4. 需要现场或人工执行的诉求同样是合理的运维意图（例如在机房布放网线、更换硬件、
               现场巡检），照常作为选项列出，由工单流程派给相应的人去执行，不要因为自己做不到就排除它。
            5. 用户补充过内容的话，再给出其他可能的推测选项：补充的和之前一致就给相同的选项，
               不一致就给新的选项。
            6. 如果下面给出了用户刚尝试过的自助方案且没有解决问题，不要再把同样的做法包装成选项，
               而应推测用户接下来需要人工完成的动作。
            7. 字段含义：title 是这条意图的简短标题（不超过 15 字）；content 是它的具体内涵，
               说明用户想达成什么、涉及哪个系统或对象；id 是从 1 开始递增的编号；
               probability 是 1-100 的整数，表示你判断的把握。

            之前会话中的草稿：
            {draft}

            用户刚尝试过的自助方案（为空表示没有走自助解决）：
            {selfServiceSolution}
            """;

    public OptionsNode(ChatClient chatClient, String systemPrompt) {
        super(chatClient, "options");
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = USER_PROMPT;
    }

    @Override
    public Flux<IntentOption> stream(OverAllState state) {
        JsonLinesOutputConverter<IntentOption> converter = new JsonLinesOutputConverter<>(IntentOption.class);
        WorkOrderProblemDraft draft = state.value("draft", WorkOrderProblemDraft.class).orElse(null);
        String solution = state.value("selfServiceSolution", String.class).orElse("");

        String userPrompt = renderUserPrompt(Map.of(
                "draft", draft == null ? "（无）" : draft.toString(),
                "selfServiceSolution", StringUtils.hasText(solution) ? solution : "（无）"));

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt + "\n\n" + converter.getFormat())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(state)))
                .stream()
                .content()
                .transform(flux -> new JsonLinesAIOutputHandler<>(converter).handle(flux))
                // action 只属于自助解决后的决策卡片。IntentOption 是共用的 DTO，
                // 生成的 JSONL 格式里带着 action，模型经常自作主张填个 WORK_ORDER；
                // 前端看到它就按决策卡渲染（不显示编号/概率），点一下还会把
                // "提工单"当成用户的动作发回来，于是又生成一遍选项。这里统一清空。
                .map(option -> {
                    option.setAction(null);
                    return option;
                });
    }

    @Override
    protected Map<String, Object> extraState(OverAllState state) {
        return Map.of("phase", WorkflowPhase.OPTIONS);
    }
}
