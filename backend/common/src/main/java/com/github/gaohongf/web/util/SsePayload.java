package com.github.gaohongf.web.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor 
@NoArgsConstructor 
public class SsePayload {
    private String id;
    private String model;
    private Long time;
}