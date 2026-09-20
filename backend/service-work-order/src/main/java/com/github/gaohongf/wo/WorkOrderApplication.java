package com.github.gaohongf.wo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient 
@SpringBootApplication 
public class WorkOrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkOrderApplication.class, args);
    }
}
