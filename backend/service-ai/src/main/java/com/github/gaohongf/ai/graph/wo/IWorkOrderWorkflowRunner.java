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

    /**
     * 这段会话里被识图节点筛出的有效现场图片。
     *
     * <p>
     * 建单时由 AI 服务直接读取，不经过浏览器 —— 见实现类上的说明。
     * 会话不存在或状态读不到时返回空列表，不阻断建单。
     */
    List<String> workOrderImages(String threadId);
}
