package com.github.gaohongf.ai.graph.wo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;

import lombok.AllArgsConstructor;

/**
 * 输入护栏：判断这一轮的用户输入是否属于运维范畴。
 *
 * <p>
 * 它放在识图和提取之前：无关输入在进多模态模型之前就被挡下，既省 token，
 * 也避免让无关内容进入后续节点的提示词。判定本身只回答 true / false，
 * 拒绝的话术由后面的 {@link GuardrailReplyNode} 输出。
 *
 * <p>
 * 状态约定：
 * <ul>
 * <li>{@code guardrail}：本轮是否放行；</li>
 * <li>{@code violationCount}：这段会话累计被拦下的次数（只增不减）；</li>
 * <li>{@code sessionEnded}：累计次数超过 {@link #MAX_VIOLATIONS}，会话被主动结束。</li>
 * </ul>
 */
@AllArgsConstructor
public class GuardrailNode implements AsyncNodeAction {

    /**
     * 允许被拦下的次数。第 {@code MAX_VIOLATIONS + 1} 次（即"超过三次"）结束会话。
     * 前几次只拒绝这一轮：用户可能只是把问题说得太随意，还应当给他继续描述的机会。
     */
    public static final int MAX_VIOLATIONS = 3;

    private final ChatClient chatClient;

    private final String systemPrompt;

    private static final String userPrompt = """
            用户输入了一段内容，可能是任何内容

            下面在这一节点的工作：
            - 识别用户当前输入的意图。
            - 如果用户的输入与运维工作无关，则输出 false
            - 如果用户的输入与运维工作有关，则输出 true
            - 如果对话历史中用户为正常交流运维相关内容，则输出 true

            用户输入：
            {userInput}
            """;

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return AsyncNodeAction.node_async((s) -> {
            Map<String, Object> map = new HashMap<>();

            // 会话已经被结束：不再调用模型。恢复出来的状态里这个标记还在，
            // 所以哪怕客户端绕过界面继续发消息，也只会再收到一次结束提示。
            if (Boolean.TRUE.equals(s.value("sessionEnded", Boolean.class).orElse(false))) {
                map.put("guardrail", false);
                map.put("sessionEnded", true);
                return map;
            }

            String userInput = s.value("userInput", String.class).orElse("");
            String conversationId = s.value("conversationId", String.class).orElse(null);
            Boolean content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(PromptTemplate
                            .builder()
                            .template(userPrompt)
                            .variables(Map.of("userInput", userInput))
                            .build()
                            .render())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .entity(Boolean.class);

            // 模型没给出明确 true 时按不放行处理：护栏宁可多挡一次，
            // 也不要让无关内容进到识图/提取里。
            boolean passed = Boolean.TRUE.equals(content);
            map.put("guardrail", passed);
            if (!passed) {
                int violations = s.value("violationCount", Integer.class).orElse(0) + 1;
                map.put("violationCount", violations);
                map.put("sessionEnded", violations > MAX_VIOLATIONS);
            }
            return map;
        }).apply(state);
    }
}
