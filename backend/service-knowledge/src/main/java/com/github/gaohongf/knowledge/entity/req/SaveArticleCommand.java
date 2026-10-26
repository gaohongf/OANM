package com.github.gaohongf.knowledge.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新建 / 编辑文章。
 *
 * <p>
 * {@code status} 用字符串而不是枚举：枚举反序列化失败会抛 Jackson 的通用 400，
 * 这里要的是带业务消息的 {@code KNOWLEDGE_STATUS_INVALID}。
 * 传 null 表示新建用 DRAFT、编辑时保持原状态。
 */
@Data
public class SaveArticleCommand {

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 200, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String title;

    @Length(max = 500, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String summary;

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 100000, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String content;

    @NotNull(message = "请选择分类")
    private Long categoryId;

    /** DRAFT / PUBLISHED；null 表示保持原状态 */
    private String status;
}
