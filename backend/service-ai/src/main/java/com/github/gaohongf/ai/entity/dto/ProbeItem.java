package com.github.gaohongf.ai.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 故障确认里的一个候选现象：一篇文章对应一张卡片。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProbeItem {

    /**
     * 文章标题，从提示词里给的文章原样复制。
     * 只用于把这一项对回检索结果（同一篇文章不能出现两次），不给用户看。
     */
    @Schema(description = "文章标题，从给出的文章里原样复制，不带书名号")
    private String article;

    /** 用户确认这种情况时的说法，例如"是打印机卡纸了" */
    @Schema(description = "用户确认这种情况时的口语，带上具体现象，例如“是打印机卡纸了”")
    private String answer;
}
