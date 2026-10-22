package com.github.gaohongf.auth.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.R;
import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserCommand {
    // 上限与登录时的 UsernameAndPassword.check() 保持一致, 避免"能创建却登录不了/反过来"
    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 2, max = 10, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String username;

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 6, max = 20, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String password;

    /**
     * 注解校验的是原始值；这里 trim 后再校验一次长度，避免首尾空格既算进用户名又能进库。
     * 密码不 trim —— 空格也是密码的一部分。
     */
    public void check() {
        if (username != null) {
            username = username.trim();
        }
        if (username == null || username.length() < 2 || username.length() > 10) {
            R.error(BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE, 2, 10);
        }
    }
}
