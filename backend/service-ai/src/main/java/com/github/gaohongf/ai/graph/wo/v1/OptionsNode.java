package com.github.gaohongf.ai.graph.wo.v1;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.github.gaohongf.ai.entity.dto.IntentOption;
import com.github.gaohongf.ai.entity.dto.WorkOrderProblemDraft;
import com.github.gaohongf.ai.util.JsonLinesAIOutputHandler;
import com.github.gaohongf.ai.util.JsonLinesOutputConverter;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class OptionsNode implements AsyncNodeAction {
    private final ChatClient chatClient;
    private final String systemPrompt;
    private static final String userPrompt;

    static {
        userPrompt = """
                你是一个运维工单系统的意图分析助手。
                用户之前提交了故障描述或一些需求，但往往描述不清。

                你在当前节点的任务是:
                1. 区分用户输入是需求还是故障，推测 3-5 个具体的用户意图作为选项，供用户选择确认。
                2. 每个选项代入用户的视角描述"我想要什么"，不要写你自己的回应。严禁出现"我无法""我不能"
                   "请提供更多信息""建议联系供应商"这类能力声明、免责说明或指路建议 —— 它们不是意图。
                3. **选项里不允许出现任何提问**。能走到这一步，就说明时间和地点都已经有了；
                   再写"请告诉我时间""请补充地点"之类的选项是错的 —— 它既占掉一个候选位置，
                   又会让用户以为自己得先回答一个早就答过的问题。
                4. 需要现场或人工执行的诉求同样是合理的运维意图（例如在机房布放网线、更换硬件、
                   现场巡检），照常作为选项列出，由工单流程派给相应的人去执行，不要因为自己做不到就排除它。
                5. 用户补充过内容的话，再给出其他可能的推测选项：补充的和之前一致就给相同的选项，
                   不一致就给新的选项。
                6. 字段含义：title 是这条意图的简短标题（不超过 15 字）；content 是它的具体内涵，
                   说明用户想达成什么、涉及哪个系统或对象；id 是从 1 开始递增的编号。

                之前会话中的草稿：
                {draft}
                """;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return AsyncNodeAction.node_async((s) -> {
            Map<String, Object> map = new HashMap<>();
            String conversationId = s.value("conversationId", String.class).orElse(null);
            WorkOrderProblemDraft draft = s.value("draft", WorkOrderProblemDraft.class).orElse(null);
            JsonLinesOutputConverter<IntentOption> converter = new JsonLinesOutputConverter<>(IntentOption.class);
            map.put("options", chatClient.prompt().system(systemPrompt).user(PromptTemplate
                    .builder()
                    .template(userPrompt)
                    .variables(Map.of("draft", draft))
                    .build()
                    .render() + "\n\n" + converter.getFormat())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content()
                    .transform(flux -> new JsonLinesAIOutputHandler<>(converter).handle(flux))
                );
            return map;
        }).apply(state);
    }
}
