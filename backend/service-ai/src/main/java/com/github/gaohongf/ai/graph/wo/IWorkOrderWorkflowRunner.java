package com.github.gaohongf.ai.graph.wo;

import java.util.List;

import com.github.gaohongf.web.sse.SseElement;

import reactor.core.publisher.Flux;

public interface IWorkOrderWorkflowRunner {
    Flux<SseElement> stream(String threadId, String userInput, List<String> images);

    Flux<SseElement> select(String threadId, Integer userSelect);

    /**
     * 用户在自助解决建议之后点击了决策卡片。
     *
     * @param action {@code SELF_RESOLVED} 或 {@code WORK_ORDER}
     */
    Flux<SseElement> decide(String threadId, String action);
}
