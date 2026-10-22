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
                - 地点：现实世界中能找到的位置，比如哪栋楼、哪一层、哪个办公室、哪个机房、哪个工位。
                  设备名、系统名、账号名本身不算地点（如「我的电脑」「打印机」「OA 系统」）：
                  如果用户只说了这些、没有说明它在哪，就返回 null，不要拿它们凑数或自行推测位置。

                如果用户明确表示不知道、记不清或没注意（如「我不知道什么时候」「地点记不清了」），
                对应字段填「未知」而不是 null —— 用户已经尽力了，流程要能继续往下走。
                只有用户完全没提到时，才给对应字段返回 null。

                用户输入：
                {userInput}

                这一轮图片的内容描述（没有图片时为空）:
                {vision_res}
                """;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return AsyncNodeAction.node_async((s) -> {
            Map<String, Object> map = new HashMap<>();
            String userInput = s.value("userInput", String.class).orElse("空");
            String vision = s.value("vision_res", String.class).orElse("");
            String conversationId = s.value("conversationId", String.class)
                    .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));

            WorkOrderProblemDraft newDraft = chatClient.prompt().system(systemPrompt).user(PromptTemplate
                    .builder()
                    .template(userPrompt)
                    .variables(Map.of("userInput", userInput, "vision_res", vision))
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
