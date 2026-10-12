package com.github.gaohongf.message;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

import com.github.gaohongf.web.rsm.ReactiveResponseBuilder;
import com.lingyun.base.rsm.annotation.EnableRsm;
@EnableRsm 
@Import(ReactiveResponseBuilder.class)
@EnableDiscoveryClient
@SpringBootApplication
public class BusinessMessageApplication {
    public static void main(String[] args) {
        SpringApplication.run(BusinessMessageApplication.class, args);
    }
}
