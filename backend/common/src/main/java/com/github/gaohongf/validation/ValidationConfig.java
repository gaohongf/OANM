package com.github.gaohongf.validation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.lingyun.base.rsm.ResponseBuilder;
import com.lingyun.base.rsm.validation.Validation2UnifyMessageErrorAdapter;

@Configuration
public class ValidationConfig {
    @Bean
    public Validation2UnifyMessageErrorAdapter validation2UnifyMessageErrorAdapter(ResponseBuilder<?> responseBuilder) {
        return new Validation2UnifyMessageErrorAdapter(responseBuilder);
    }
}
