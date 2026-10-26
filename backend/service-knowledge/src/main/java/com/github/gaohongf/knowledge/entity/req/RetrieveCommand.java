package com.github.gaohongf.knowledge.entity.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 内部检索请求（给未来的 service-ai 用） */
@Data
public class RetrieveCommand {

    @NotBlank(message = "检索内容不能为空")
    private String query;

    /** 返回的片段数；不传用服务端默认值 */
    @Min(value = 1, message = "topK 至少为 1")
    @Max(value = 50, message = "topK 不能超过 50")
    private Integer topK;

    /** 只在该分类内检索；不传表示全部分类 */
    private Long categoryId;
}
