package com.github.gaohongf.ai.graph.wo.v2;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.entity.dto.WorkOrderDraft;
import com.github.gaohongf.ai.util.DateTimeTools;
import com.lingyun.base.rsm.exception.RequestException;

import reactor.core.publisher.Flux;

/**
 * 把用户选中的工单意图整理成一张待处理的工单。
 *
 * <p>
 * 不做流式：这里要的是一张可以整体校验、整体编辑的单子，不是逐字给用户看的文本。
 * 结果仍包成单元素 Flux 带出去，因为启动器只把 {@code StreamingOutput} 转成 SSE 帧。
 *
 * <p>
 * 工单没有"解决方法"字段，提示词里也不再让模型写处理步骤 —— 具体怎么做由受理人处理时决定。
 */
public class DraftNode extends AINode {

    private static final String SYSTEM_PROMPT = """
            用户已经在之前的对话里选择了一个意图，现在需要你协助把它整理成一张待处理的工单。

            你的任务是：
            1. 结合用户最初的问题描述和后续的补充，给出工单标题、类型、优先级和描述。
            2. 描述控制在 80-200 字，说清问题现象、影响范围、时间地点。
            3. 不要写解决方法、处理步骤或建议 —— 这张工单没有该字段，具体怎么处理由受理人决定。
            4. 不要被用户带歪：用户自己给的判断和建议只作参考，最终以你的专业判断为准。
            5. 如果用户选择的意图与他最初的描述明显矛盾，以用户选择的那个为准。
            6. 工单描述中要把用户的模糊时间描述转换为更具体的时间，不可以出现"昨天""今早"
               这类相对的模糊时间描述。你可以借助时间工具获得当前时间，来判断相对具体的事发时间。

            字段取值要求：
            - type 只能是 demand（需求）或 fault（故障）。
            - priority 只能是 low、medium、high、urgent 之一。
            """;

    private final DateTimeTools dateTimeTools;

    public DraftNode(ChatClient chatClient, DateTimeTools dateTimeTools) {
        super(chatClient);
        this.systemPrompt = SYSTEM_PROMPT;
        this.dateTimeTools = dateTimeTools;
    }

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        Integer userSelect = state.value("userSelect", Integer.class).orElse(null);

        WorkOrderDraft draft = chatClient.prompt()
                .tools(dateTimeTools)
                .system(systemPrompt)
                .user("用户选择：" + userSelect)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(state)))
                .call()
                .entity(WorkOrderDraft.class);

        if (draft == null) {
            throw new RequestException("模型没有返回工单内容");
        }
        return Map.of("workOrderDraft", Flux.just(draft));
    }
}
