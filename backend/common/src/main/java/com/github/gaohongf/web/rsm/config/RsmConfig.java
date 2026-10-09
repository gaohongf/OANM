package com.github.gaohongf.web.rsm.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.github.gaohongf.auth.rsm.AuthRsm;

@AutoConfiguration  
public class RsmConfig {
    @Bean 
    public AuthRsm authRsm() {
        return new AuthRsm();
    }
}
