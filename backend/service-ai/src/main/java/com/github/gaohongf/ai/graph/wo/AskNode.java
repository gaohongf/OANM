package com.github.gaohongf.ai.graph.wo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;

@AllArgsConstructor
public class AskNode implements AsyncNodeAction {
    private final ChatClient chatClient;
    private final String systemPrompt;
    private static final String userPrompt;

    static {
        userPrompt = """
                你是一个运维工单系统的意图分析助手。
                用户之前的描述缺少了一些关键信息。

                你在当前节点的任务是:
                1. 用日常说话的语气把缺的那一样问清楚，可以顺带给一两个例子帮用户回答。
                   （时间如"昨天上午"，地点如"行政楼行政办公室"）。
                2. 不要输出编号、不要列清单、不要把用户说过的话复述一遍 —— 用户要看到的只是一句问话。
                3. 一次只问缺的那一样；两样都缺就在同一句话里一起问，不要拆成两轮。
                4. 如果缺少多样信息，询问和举例时要自然的组合进问题语句中，避免生硬和机械式对话。
                5. 已经答过的那样不要再问，用户这一轮补上的信息要认账。
                6. 提问要根据之前的已有信息自然追问，曾经举过例子了就不需要再举了。
                7. 你当前节点无需结构化输出。

                缺少的关键信息如下:
                {missing}
                """;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return AsyncNodeAction.node_async((s) -> {
            Map<String, Object> map = new HashMap<>();
            String conversationId = s.value("conversationId", String.class).orElse(null);
            Flux<String> content = chatClient.prompt().system(systemPrompt).user(PromptTemplate
                    .builder()
                    .template(userPrompt)
                    .variables(Map.of("missing", s.value("missing", List.class)
                            .map(miss -> miss.stream().reduce((a, b) -> a + "\n" + b).orElse("").toString())
                            .get()))
                    .build()
                    .render())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content();

            map.put("messages", content);
            return map;
        }).apply(state);
    }
}
