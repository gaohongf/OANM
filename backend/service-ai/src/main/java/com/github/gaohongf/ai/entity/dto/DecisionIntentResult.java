package com.github.gaohongf.ai.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户在自助解决建议之后的回复意图。
 *
 * <p>
 * 用对象而不是直接 {@code entity(String.class)}：结构化输出转换器对对象最稳定，
 * 模型也不会把答案包成一段解释。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecisionIntentResult {

    @Schema(description = "SELF_RESOLVED / WORK_ORDER / UNCLEAR")
    private String intent;
}
