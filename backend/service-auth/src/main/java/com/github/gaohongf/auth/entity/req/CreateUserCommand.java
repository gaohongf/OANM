package com.github.gaohongf.auth.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.R;
import com.lingyun.base.rsm.validation.BaseValidationRsm;

import lombok.Data;

@Data
public class CreateUserCommand {
    @Length(min = 2, max = 6, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String username;
    @Length(min = 6, max = 20, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String password;

    public void check() {
        if (username == null || username.length() < 2 || username.length() > 10)
            R.error(BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE, 2, 10);
        if (password == null || password.length() < 6 || password.length() > 20)
            R.error(BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE, 6, 20);
    }
}
