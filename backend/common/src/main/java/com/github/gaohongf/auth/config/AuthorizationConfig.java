package com.github.gaohongf.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.github.gaohongf.auth.interceptor.AuthorizationInterceptor;

@Configuration
public class AuthorizationConfig implements WebMvcConfigurer {
    
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new AuthorizationInterceptor())
        .addPathPatterns("/auth/**");
    }
}
