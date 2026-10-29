package com.github.gaohongf.ai.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单张图片的识别结果：这张图对本次运维问题有没有用，以及图里是什么。
 *
 * <p>
 * 每张图单独问一次模型，而不是把几张图一起丢进去让它讲一段话 ——
 * 只有"一问一图"才能把结论明确地对应回具体的图片地址，进而决定哪几个链接
 * 跟着工单走。多图合问时模型只能给出混在一起的描述，没法可靠地拆回 URL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageCheck {

    @Schema(description = "这张图片是否与本次运维问题有关。现场照片、设备外观、报错屏幕、"
            + "软件界面截图等为 true；表情包、梗图、与设备无关的照片为 false")
    private Boolean relevant;

    @Schema(description = "与问题有关时，用一两句话描述图中与问题相关的内容；"
            + "无关时，用一句话说明它实际是什么（例如“表情包”）")
    private String description;
}
