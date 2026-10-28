package com.github.gaohongf.ai.graph.wo.v2;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.graph.OverAllState;

/**
 * 输入护栏：判断这一轮的用户输入是否属于运维范畴。
 *
 * <p>
 * 点决策卡片时不经过模型：那是用户对已有建议的明确回答，不是一段需要判断归属的输入。
 * 直接放行，同时避免"帮我提工单"这种话被误判成无关内容。
 *
 * <p>
 * 文字输入仍然交给模型，但**必须带上会话阶段**：自助建议之后的一句"好了谢谢"，
 * 单独看是句客套话，放在 DECISION 阶段里就是对"解决了没有"的回答。
 * 只凭这一句话做判断，模型会把它拦掉。
 */
public class GuardrailNode extends AINode {

    /**
     * 允许被拦下的次数。第 {@code MAX_VIOLATIONS + 1} 次结束会话。
     * 前几次只拒绝这一轮：用户可能只是把问题说得太随意。
     */
    public static final int MAX_VIOLATIONS = 3;

    /**
     * 用户提示词只放"这一轮说了什么"和"会话现在停在哪儿"。
     *
     * <p>
     * 会话阶段必须显式写出来：光靠对话历史，模型看到一句"好了谢谢"很容易当成孤立的一句话，
     * 而它其实是上一步自助建议的回答。历史内容照旧由 ChatMemory 带上，这里补的是
     * 模型自己推不出来的结论。
     */
    private static final String USER_PROMPT = """
            本次会话当前阶段：{phase}

            用户这一轮输入：
            {userInput}
            """;

    public GuardrailNode(ChatClient chatClient, String systemPrompt) {
        super(chatClient);
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = USER_PROMPT;
    }

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        Map<String, Object> output = new HashMap<>();

        // 会话已经被结束：不再调用模型
        if (Boolean.TRUE.equals(state.value("sessionEnded", Boolean.class).orElse(false))) {
            output.put("guardrail", false);
            output.put("sessionEnded", true);
            return output;
        }

        // 决策动作是明确的操作，不需要再过一遍内容护栏
        if (StringUtils.hasText(state.value("decisionAction", String.class).orElse(""))) {
            output.put("guardrail", true);
            return output;
        }

        String userInput = state.value("userInput", String.class).orElse("");
        Boolean content = prompt(Map.of("userInput", userInput, "phase", phaseHint(state)))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(state)))
                .call()
                .entity(Boolean.class);

        // 模型没给出明确 true 时按不放行处理：护栏宁可多挡一次
        boolean passed = Boolean.TRUE.equals(content);
        output.put("guardrail", passed);
        if (!passed) {
            int violations = state.value("violationCount", Integer.class).orElse(0) + 1;
            output.put("violationCount", violations);
            output.put("sessionEnded", violations > MAX_VIOLATIONS);
        }
        return output;
    }

    /**
     * 把会话阶段翻成一句人话给模型看。
     *
     * <p>
     * 阶段标记本身是给路由用的（{@code DECISION} 之类），直接塞进提示词模型未必懂；
     * 写成"正在等用户回答是否解决"，模型才知道一句"好了谢谢"是在回答什么。
     */
    private static String phaseHint(OverAllState state) {
        return switch (WorkflowPhase.of(state)) {
            case WorkflowPhase.DECISION -> "已经给出自助解决步骤，正在等用户回答「是否解决」";
            case WorkflowPhase.OPTIONS -> "已经给出工单意图选项，正在等用户挑选一项或补充说明";
            case WorkflowPhase.WORK_ORDER -> "已经确定要人工处理，正在补齐时间、地点等信息";
            default -> "正在了解用户遇到的问题";
        };
    }
}
