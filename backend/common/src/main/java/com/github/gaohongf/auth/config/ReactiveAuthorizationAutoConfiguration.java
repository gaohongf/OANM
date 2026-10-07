package com.github.gaohongf.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;

import com.github.gaohongf.auth.interceptor.ReactiveAuthorizationHandlerAdapter;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(name = "oanm.auth.enabled", havingValue = "true", matchIfMissing = true)
public class ReactiveAuthorizationAutoConfiguration {
    
    @Bean 
    public ReactiveAuthorizationHandlerAdapter reactiveAuthorizationHandlerAdapter(RequestMappingHandlerAdapter requestMappingHandlerAdapter){
        return new ReactiveAuthorizationHandlerAdapter(requestMappingHandlerAdapter);
    }

}
