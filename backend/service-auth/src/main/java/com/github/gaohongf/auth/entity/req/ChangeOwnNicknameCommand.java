package com.github.gaohongf.auth.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 修改自己的昵称。昵称只用于展示，长度与 users.nickname 列保持一致。 */
@Data
public class ChangeOwnNicknameCommand {
    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(max = 64, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String nickname;

    /** 去掉首尾空格；纯空白已经被 {@link NotBlank} 拦住。 */
    public void check() {
        if (nickname != null) {
            nickname = nickname.trim();
        }
    }
}
