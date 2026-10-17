package com.github.gaohongf.ai.chat.sse;

import com.github.gaohongf.web.sse.SseElement;

/**
 * @author gaohongf
 * ChatEvent
 * @param n node
 * @param d data
 */
public record ChatEvent(String n, Object d) implements SseElement {
    public String event() {
        return "chat";
    }
}
