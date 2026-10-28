package com.github.gaohongf.ai.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentOption {
    @Schema(description = "编号, 一个从1开始递增的整数")
    private Integer id;
    private String title;
    private String content;
    @Schema(description = "概率, 一个1-100的整数")
    private Integer probability;
    /**
     * 决策卡片的动作。普通意图选项为 null；
     * 自助解决后的选择卡片取 SELF_RESOLVED / WORK_ORDER。
     */
    @Schema(description = "决策动作: SELF_RESOLVED / WORK_ORDER，普通意图选项为空")
    private String action;
}
