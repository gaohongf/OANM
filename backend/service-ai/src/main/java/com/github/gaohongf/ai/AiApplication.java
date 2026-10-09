package com.github.gaohongf.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

import com.lingyun.base.rsm.RsmAutoConfiguration;

@Import(RsmAutoConfiguration.class)
@SpringBootApplication
@EnableDiscoveryClient 
public class AiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
