package com.github.gaohongf.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

import com.github.gaohongf.knowledge.config.KnowledgeProperties;

/**
 * 运维知识库服务。
 *
 * <p>
 * 索引在事务提交后用独立线程池异步执行，因此需要 {@code @EnableAsync}。
 */
@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication
@EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}
