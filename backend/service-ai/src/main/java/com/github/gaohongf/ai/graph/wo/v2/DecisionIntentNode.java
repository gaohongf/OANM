package com.github.gaohongf.ai.graph.wo.v2;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.entity.dto.DecisionIntentResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 用自然语言回答"解决了没有"时的意图分类器。
 *
 * <p>
 * 用户可以不点卡片，直接说"好了""还是不行""帮我提工单"。这一节点只做分类，
 * 不产出用户可见的文字；后续边根据结果去生成自助记录、回到工单选项，
 * 或者重新展示两张卡片。不确定时一律 UNCLEAR —— 猜错会直接建错单。
 */
@Slf4j
public class DecisionIntentNode extends AINode {

    private static final String SYSTEM_PROMPT = """
            你是运维工单系统的对话路由器。用户已经看到了自助解决建议，现在回复了一句话。
            判断用户这句话属于哪一类，只能从下面三个值里选一个：
            - SELF_RESOLVED: 用户明确表示问题已经解决（如"好了""可以了""解决了""按你说的弄好了"）。
            - WORK_ORDER: 用户表示没解决、仍然有问题，或明确要求提工单/找人工（如"还是不行""帮我提工单""找个人来看看"）。
            - UNCLEAR: 其他情况（问细节、只说谢谢但没说是否解决、话题不明确）。

            不要根据语气猜测；不确定就输出 UNCLEAR。
            """;

    private static final String USER_PROMPT = """
            自助建议：
            {solution}

            用户回复：
            {userInput}
            """;

    public DecisionIntentNode(ChatClient chatClient) {
        super(chatClient);
        this.systemPrompt = SYSTEM_PROMPT;
        this.userPromptTemplate = USER_PROMPT;
    }

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        String solution = state.value("selfServiceSolution", String.class).orElse("");
        String userInput = state.value("userInput", String.class).orElse("");

        Map<String, Object> output = new HashMap<>();
        try {
            DecisionIntentResult result = prompt(Map.of(
                    "solution", StringUtils.hasText(solution) ? solution : "（无）",
                    "userInput", userInput))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(state)))
                    .call()
                    .entity(DecisionIntentResult.class);

            String intent = result == null ? null : result.getIntent();
            DecisionAction action = DecisionAction.parse(intent).orElse(null);
            output.put("decisionIntent", action == null ? "UNCLEAR" : action.name());
            if (action == DecisionAction.WORK_ORDER) {
                // 用户明确要人工：转工单轨道，缺的时间地点在这之后才补
                output.put("phase", WorkflowPhase.WORK_ORDER);
            }
        } catch (RuntimeException ex) {
            log.warn("决策意图识别失败，按不明确处理: {}", ex.getMessage());
            output.put("decisionIntent", "UNCLEAR");
        }
        return output;
    }
}
