package com.github.gaohongf.validation;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import com.lingyun.base.rsm.ResponseBuilder;
import com.lingyun.base.rsm.validation.Validation2UnifyMessageErrorAdapter;

@AutoConfiguration 
public class ValidationConfig {
    @Bean
    @ConditionalOnBean(ResponseBuilder.class)
    public Validation2UnifyMessageErrorAdapter validation2UnifyMessageErrorAdapter(ResponseBuilder<?> responseBuilder) {
        return new Validation2UnifyMessageErrorAdapter(responseBuilder);
    }
}
