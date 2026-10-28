package com.github.gaohongf.ai.graph.wo.v2;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;

/**
 * 不调用大模型的节点。
 *
 * <p>
 * 节点动作的骨架（把同步的 {@link #execute} 包成图需要的异步形式）只写一次，
 * 子类只关心"读哪些状态、写哪些状态"。{@link AINode} 复用同一套骨架，
 * 所以 AI 节点与非 AI 节点在启动器里是同一种东西：都能直接交给
 * {@code StateGraph.addNode(...)}。
 */
public abstract class WorkflowNode implements AsyncNodeAction {

    @Override
    public final CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return AsyncNodeAction.node_async(this::execute).apply(state);
    }

    /**
     * 读状态、算结果。抛出的运行时异常由工作流统一转成 SSE 错误帧。
     */
    protected abstract Map<String, Object> execute(OverAllState state);
}
