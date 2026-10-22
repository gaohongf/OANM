package com.github.gaohongf.auth.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改自己的密码。
 *
 * <p>
 * 旧密码只校验非空、不限制长度：用户输错时应该得到"当前密码错误"，
 * 而不是因为长度不符合新密码规则先被拦下。
 */
@Data
public class ChangeOwnPasswordCommand {
    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    private String oldPassword;

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 6, max = 20, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String newPassword;
}
