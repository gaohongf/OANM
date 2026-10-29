package com.github.gaohongf.ai.entity.dto;

import java.util.List;

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

    /**
     * 本次会话里被识图节点判定为有效的现场图片。
     * <p>
     * 不交给模型生成：它是工作流状态里的事实，模型没有能力也不应该决定
     * 哪张图跟着工单走。生成草稿的节点在模型返回之后把它原样填进来。
     */
    @Schema(description = "现场图片链接")
    private List<String> imageUrls;
}
