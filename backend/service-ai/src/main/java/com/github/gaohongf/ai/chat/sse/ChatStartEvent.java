package com.github.gaohongf.ai.chat.sse;

import com.github.gaohongf.web.sse.SseElement;

public record ChatStartEvent(String modelName, Long time) implements SseElement{
    @Override
    public String event() {
        return "start";
    }
}
