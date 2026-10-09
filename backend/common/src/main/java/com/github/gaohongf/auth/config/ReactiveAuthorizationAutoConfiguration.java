package com.github.gaohongf.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxRegistrations;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;

import com.github.gaohongf.auth.interceptor.ReactiveAuthorizationRequestMappingHandlerAdapter;
import com.lingyun.base.rsm.ResponseBuilder;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(name = "oanm.auth.enabled", havingValue = "true", matchIfMissing = true)
public class ReactiveAuthorizationAutoConfiguration implements WebFluxRegistrations {
    private final ResponseBuilder<?> responseBuilder;

    @Override
    public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
        return new ReactiveAuthorizationRequestMappingHandlerAdapter(responseBuilder);
    }

}
