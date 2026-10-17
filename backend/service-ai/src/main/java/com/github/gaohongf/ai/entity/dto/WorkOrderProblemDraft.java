package com.github.gaohongf.ai.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderProblemDraft {
    /**
     * 提取出的时间描述
     */
    @Schema(description = "时间描述")
    private String userTimeDescription;
    /**
     * 提取出的地点描述
     */
    @Schema(description = "地点描述")
    private String userLocationDescription;
    /**
     * 用户提取出的原始问题
     */
    @Schema(description = "用户原始问题")
    private String userMessages;
}
