package com.github.gaohongf.software.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增 / 编辑软件。
 *
 * <p>
 * 编辑时 {@code enabled} 传 null 表示保持原值；新建时 null 视为启用。
 */
@Data
public class SaveSoftwareCommand {

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 200, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String name;

    @Length(max = 100, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String vendor;

    @NotNull(message = "请选择分类")
    private Long categoryId;

    @Length(max = 2000, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String description;

    private Boolean enabled;
}
