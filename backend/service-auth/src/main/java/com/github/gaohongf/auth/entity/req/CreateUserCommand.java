package com.github.gaohongf.auth.entity.req;

import com.lingyun.base.rsm.R;
import com.lingyun.base.rsm.validation.BaseValidationRsm;

import lombok.Data;

@Data
public class CreateUserCommand {
    private String username;
    private String password;

    public void check() {
        if (username == null || username.length() < 2 || username.length() > 10)
            R.error(BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE, 2, 10);
        if (password == null || password.length() < 6 || password.length() > 20)
            R.error(BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE, 6, 20);
    }
}
