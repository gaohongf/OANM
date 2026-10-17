package com.github.gaohongf.web.util;

import com.github.gaohongf.web.sse.SseElement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor 
@NoArgsConstructor 
public class SsePayload implements SseElement {
    private String id;
    private String model;
    private Long time;

    @Override
    public String event() {
        return "start";
    }
}