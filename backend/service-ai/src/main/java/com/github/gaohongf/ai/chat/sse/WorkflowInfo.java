package com.github.gaohongf.ai.chat.sse;

import com.github.gaohongf.web.sse.SseElement;

public record WorkflowInfo(String threadId) implements SseElement {
    @Override
    public String event() {
        return "workflow";
    }
}
