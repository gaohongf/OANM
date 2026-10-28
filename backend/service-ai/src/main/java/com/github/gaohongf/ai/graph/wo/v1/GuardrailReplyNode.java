package com.github.gaohongf.ai.graph.wo.v1;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 护栏拦下输入后，向用户输出一段固定的话。
 *
 * <p>
 * 为什么单独成节点、而不是在 {@link GuardrailNode} 里顺手写进状态：
 * 前端要区分两种结局 —— 普通拒绝（还能继续描述）和会话已结束（必须重新开始），
 * 而 SSE 帧里唯一能带出这个区别的就是节点名。所以这里用两个不同类型的实例
 * （{@code guardrail_reject} / {@code guardrail_end}），文案从外面传进来。
 */
@AllArgsConstructor
public class GuardrailReplyNode implements AsyncNodeAction {

    private final String text;

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return AsyncNodeAction.node_async(s -> Map.of(
                // 和 AskNode 同一条通道：Flux 里的文字会被工作流当作流式输出发给前端
                "messages", Flux.just(text)))
                .apply(state);
    }
}
