package com.github.gaohongf.ai.graph.wo.v2;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.entity.dto.WorkOrderDraft;
import com.github.gaohongf.ai.util.DateTimeTools;
import com.lingyun.base.rsm.exception.RequestException;

import reactor.core.publisher.Flux;

/**
 * 用户自己解决了问题之后，整理一条"自助解决记录"。
 *
 * <p>
 * 记录复用普通工单的字段（标题、类型、优先级、描述），但没有解决方法 ——
 * 用户已经处理完了，这张单的作用是留档与统计。状态和受理人由工单服务在创建时确定。
 */
public class SelfResolvedDraftNode extends AINode {

    private static final String SYSTEM_PROMPT = """
            用户的问题此前已经通过自助解决处理完毕。现在请你把这件事整理成一条"自助解决记录"，
            用于留档和统计。

            你的任务是：
            1. 结合整段对话，给出标题、类型、优先级和记录描述。
            2. 描述控制在 80-200 字，写清问题现象、影响范围，以及用户已按建议自行处理并解决。
            3. 不要写解决步骤、命令或建议 —— 这条记录没有"解决方法"字段。
            4. 不要编造用户没说过的信息；时间、地点如果对话里有就写进描述。
            5. 如果对话里信息不足，就按已有信息保守描述，不要猜测。
            6. 描述中把模糊时间写具体，不要出现"昨天""今早"这类相对时间。
               你可以借助时间工具获得当前时间。

            字段取值要求：
            - type 只能是 demand（需求）或 fault（故障）。
            - priority 只能是 low、medium、high、urgent 之一。
            """;

    private final DateTimeTools dateTimeTools;

    public SelfResolvedDraftNode(ChatClient chatClient, DateTimeTools dateTimeTools) {
        super(chatClient);
        this.systemPrompt = SYSTEM_PROMPT;
        this.dateTimeTools = dateTimeTools;
    }

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        WorkOrderDraft draft = chatClient.prompt()
                .tools(dateTimeTools)
                .system(systemPrompt)
                .user("用户已经解决了问题，请生成自助解决记录。")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(state)))
                .call()
                .entity(WorkOrderDraft.class);

        if (draft == null) {
            throw new RequestException("模型没有返回自助解决记录");
        }
        // 自助解决记录同样保留对话里筛出的现场图片，供用户提交前确认
        draft.setImageUrls(state.value("workOrderImages", List.class)
                .map(items -> items.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList())
                .orElse(List.of()));
        return Map.of("workOrderDraft", Flux.just(draft));
    }
}
