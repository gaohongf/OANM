package com.github.gaohongf.ai.entity.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型对"知识库片段能否解决这个问题"的判断与解决步骤。
 *
 * <p>
 * {@code usable} 与 {@code solution} 是给路由用的：只有 usable 为 true 且步骤非空，
 * 工作流才会继续走自助解决；否则直接跳过，转去生成工单。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelfServicePlan {

    @Schema(description = "知识库片段是否足以解决用户的问题")
    private Boolean usable;

    /**
     * 列出步骤之前的一句自然引导，如"可以按下面的步骤看看能不能解决？"。
     *
     * <p>
     * 它单独成字段是为了让界面把它排成一段话、步骤排成编号列表；
     * 混在 {@code solution} 里前端就只能猜哪一行是引导。可为空。
     */
    @Schema(description = "引出解决步骤的一句引导；没有则返回空字符串")
    private String intro;

    @Schema(description = "给用户看的解决步骤，每句一行；不可用时为空")
    private String solution;

    @Schema(description = "这次答复用到的知识库文章标题：从片段开头的《》里原样复制，不带书名号和“第 N 段”；没用到的片段不要列")
    private List<String> sources;
}
