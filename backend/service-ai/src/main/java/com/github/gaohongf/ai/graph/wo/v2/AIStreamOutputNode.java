package com.github.gaohongf.ai.graph.wo.v2;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;

import com.alibaba.cloud.ai.graph.OverAllState;

import reactor.core.publisher.Flux;

/**
 * 产出是流、并且要直接给前端看的 AI 节点。
 *
 * <p>
 * 节点的输出 map 里放一个 Flux，工作流才会把它变成 {@code StreamingOutput}。
 * {@code outputKey} 决定它落在哪个状态键上 —— 追问/回答用 {@code messages}，
 * 选项用 {@code options}。需要额外写状态的子类覆盖 {@link #extraState} 即可。
 *
 * @param <Output> 流里每个元素的类型
 */
public abstract class AIStreamOutputNode<Output> extends AINode {

    private final String outputKey;

    protected AIStreamOutputNode(ChatClient chatClient, String outputKey) {
        super(chatClient);
        this.outputKey = outputKey;
    }

    @Override
    protected final Map<String, Object> execute(OverAllState state) {
        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, stream(state));
        output.putAll(extraState(state));
        return output;
    }

    /** 子类可以在这里补写状态键。默认不写。 */
    protected Map<String, Object> extraState(OverAllState state) {
        return Map.of();
    }

    public abstract Flux<Output> stream(OverAllState state);
}
