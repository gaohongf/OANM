package com.github.gaohongf.auth.register;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = "org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping")
class ReactiveServiceEndpointsScannerConfiguration {

    @Bean
    ServiceEndpointsScanner reactiveServiceEndpointsScanner(
            org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping hm) {
        return new ReactiveServiceEndpointsScanner(hm);
    }
}