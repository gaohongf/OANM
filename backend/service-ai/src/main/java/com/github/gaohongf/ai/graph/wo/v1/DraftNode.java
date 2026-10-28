package com.github.gaohongf.ai.graph.wo.v1;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.github.gaohongf.ai.entity.dto.WorkOrderDraft;
import com.github.gaohongf.ai.util.DateTimeTools;
import com.lingyun.base.rsm.exception.RequestException;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 把用户选中的意图整理成一张工单草稿。
 *
 * <h2>为什么不做流式</h2>
 * 这里要的是一张可以整体校验、整体编辑的单子，不是逐字给用户看的文本。一次
 * {@code .call().entity(...)} 拿回完整对象，前端在等的时候显示骨架即可 ——
 * 逐行解析那套容错（模型可能多写一句话、少给一个字段）也就不需要了。
 *
 * <h2>为什么结果也包成 Flux</h2>
 * 启动器只把 {@code StreamingOutput} 转成 SSE 帧，而图只有在节点的输出 map 里发现
 * Flux 时才会产生它。所以这里借一条只发一个元素的 Flux 把草稿带出去 ——
 * 与 {@link OptionsNode} 用 {@code Flux<IntentOption>} 出流是同一套机制。
 *
 * <h2>时间工具</h2>
 * 提示词要求把"昨天上午"这类相对时间写具体，模型需要知道现在是什么时候。
 */
@AllArgsConstructor
public class DraftNode implements AsyncNodeAction {

    private final ChatClient chatClient;
    private final DateTimeTools dateTimeTools;

    private static final String SYSTEM_PROMPT = """
            用户已经在之前的对话里选择了一个意图，现在需要你协助把它整理成一张工单。

            你的任务是：
            1. 结合用户最初的问题描述和后续的补充，给出工单标题、类型、优先级、描述和解决方法。
            2. 描述控制在 80-200 字；解决方法要详细、可执行、分步骤。
            3. 不要被用户带歪：用户自己给的判断和建议只作参考，最终以你的专业判断为准。
            4. 如果用户选择的意图与他最初的描述明显矛盾，以用户选择的那个为准。
            5. 解决方法面向**执行这张工单的人**来写（可能是现场人员或供应商），只写该怎么做；
               不要写"我无法…""请联系…"这类能力声明或免责说明，需要现场操作的步骤照常写出来。
            6. 工单描述中要把用户的模糊时间描述转换为更具体的时间，不可以出现"昨天""今早"
               这类相对的模糊时间描述。你可以借助时间工具获得当前时间，来判断相对具体的事发时间。

            字段取值要求：
            - type 只能是 demand（需求）或 fault（故障）。
            - priority 只能是 low、medium、high、urgent 之一。
            """;

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return AsyncNodeAction.node_async((s) -> {
            Map<String, Object> map = new HashMap<>();
            String conversationId = s.value("conversationId", String.class).orElse(null);
            Integer userSelect = s.value("userSelect", Integer.class).orElse(null);

            WorkOrderDraft draft = chatClient.prompt()
                    .tools(dateTimeTools)
                    .system(SYSTEM_PROMPT)
                    .user("用户选择：" + userSelect)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .entity(WorkOrderDraft.class);

            if (draft == null) {
                // 交给流去报错：前端会收到一条 error 帧，而不是一个永远等不到内容的骨架
                throw new RequestException("模型没有返回工单内容");
            }
            map.put("workOrderDraft", Flux.just(draft));
            return map;
        }).apply(state);
    }
}
