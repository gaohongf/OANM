package com.github.gaohongf.ai.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型整理出来的一张工单草稿。
 *
 * <p>
 * 它就是前端 Drawer 里要预填的那张表：字段名与提交接口逐字对应，前端不再做一层映射。
 * {@code type} / {@code priority} 收的是模型给的原始字符串（提示词约束成 demand/fault、
 * low/medium/high/urgent），由前端 {@code normalizeType} / {@code normalizePriority} 宽松归一化 ——
 * 模型偶尔会写成中文或大小写不一，这类值不该让整张单子作废。
 *
 * <p>
 * 没有 {@code user_input_N} 之类的字段：用户原话由前端本地那份轮次提供（那是他自己敲的），
 * 模型转述会改写、会丢信息，不再作为权威来源。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderDraft {

    @Schema(description = "工单标题")
    private String title;

    @Schema(description = "工单类型：demand / fault")
    private String type;

    @Schema(description = "优先级：low / medium / high / urgent")
    private String priority;

    @Schema(description = "整理后的工单描述")
    private String problemDescription;
}
