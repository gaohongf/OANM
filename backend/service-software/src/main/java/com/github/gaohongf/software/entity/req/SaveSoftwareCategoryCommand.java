package com.github.gaohongf.software.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 新增 / 编辑软件分类 */
@Data
public class SaveSoftwareCategoryCommand {

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 64, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String name;

    @NotNull(message = "请填写排序值")
    @Min(value = 0, message = "排序不能小于 0")
    @Max(value = 9999, message = "排序不能大于 9999")
    private Integer sort;
}
