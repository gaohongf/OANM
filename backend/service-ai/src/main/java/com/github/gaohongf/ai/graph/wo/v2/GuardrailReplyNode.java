package com.github.gaohongf.ai.graph.wo.v2;

import java.util.Map;

import com.alibaba.cloud.ai.graph.OverAllState;

import reactor.core.publisher.Flux;

/**
 * 护栏拦下输入后，向用户输出一段固定的话。
 *
 * <p>
 * 两个结局用两个节点名区分（{@code guardrail_reject} / {@code guardrail_end}），
 * 前端据此决定是"还能继续描述"还是"必须重新开始"。
 */
public class GuardrailReplyNode extends WorkflowNode {

    private final String text;

    public GuardrailReplyNode(String text) {
        this.text = text;
    }

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        return Map.of("messages", Flux.just(text));
    }
}
