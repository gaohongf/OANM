package com.github.gaohongf.auth.register;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping")
public class ServletServiceEndpointsScannerConfiguration {
    @Bean
    public ServiceEndpointsScanner servletSerivceEndpointsScanner(
            org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return new ServletServiceEndpointsScanner(requestMappingHandlerMapping);
    }
}
