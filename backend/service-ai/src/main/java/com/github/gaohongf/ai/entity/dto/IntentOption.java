package com.github.gaohongf.ai.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IntentOption {
    @Schema(description = "编号, 一个从1开始递增的整数")
    private Integer id;
    private String title;
    private String content;
    @Schema(description = "概率, 一个1-100的整数")
    private Integer probability;
}
