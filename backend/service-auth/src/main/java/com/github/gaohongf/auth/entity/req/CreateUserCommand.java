package com.github.gaohongf.auth.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.validation.BaseValidationRsm;

import lombok.Data;

@Data
public class CreateUserCommand {
    @Length(min = 2, max = 6, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String username;
    @Length(min = 6, max = 20, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String password;
}
