package com.github.gaohongf.ai.graph.wo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.github.gaohongf.ai.entity.dto.WorkOrderProblemDraft;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ExtractNode implements AsyncNodeAction {
    private final ChatClient chatClient;
    private final String systemPrompt;
    private static final String userPrompt;

    static {
        userPrompt = """
                你是一个运维工单系统的意图分析助手。
                用户可能一次或多次提交了故障描述或一些需求，但往往描述不清。

                你在当前节点的任务是:
                提取时间和地点描述。
                检查的是用户**到目前为止说过的所有话**（含前面几轮），而不是只看最后一句：
                - 时间：事情发生在什么时候。模糊的就行（昨天上午 / 上周三 / 明天下午），不必精确到分钟。
                - 地点：事情发生在哪里。哪栋楼、哪个办公室、哪台设备、哪个系统都算。

                如果用户未提及则给对应字段返回 null

                用户输入：
                {userInput}
                """;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return AsyncNodeAction.node_async((s) -> {
            Map<String, Object> map = new HashMap<>();
            String userInput = s.value("userInput", String.class).orElse(null);
            String conversationId = s.value("conversationId", String.class)
                    .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
            WorkOrderProblemDraft newDraft = chatClient.prompt().system(systemPrompt).user(PromptTemplate
                    .builder()
                    .template(userPrompt)
                    .variables(Map.of("userInput", userInput))
                    .build()
                    .render())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .entity(WorkOrderProblemDraft.class);
            map.put("draft", newDraft);
            map.put("conversationId", conversationId);
            return map;
        }).apply(state);
    }
}
