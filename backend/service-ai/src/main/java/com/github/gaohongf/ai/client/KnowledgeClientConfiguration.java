package com.github.gaohongf.ai.client;

import java.util.List;

import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 调用知识库服务的客户端配置。
 *
 * <p>
 * 与 {@link WorkOrderClientConfiguration} 同样的理由：只产出作用范围明确的
 * {@code WebClient}，不注册 {@code @LoadBalanced WebClient.Builder}，
 * 否则会顶掉 Spring AI 用来访问模型 API 的那个 Builder。
 */
@Configuration
public class KnowledgeClientConfiguration {

    private static final String KNOWLEDGE_SERVICE = "http://service-knowledge";

    @Bean
    public WebClient knowledgeWebClient(WebClient.Builder builder,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        return builder
                .baseUrl(KNOWLEDGE_SERVICE)
                .filter(new ReactorLoadBalancerExchangeFilterFunction(
                        loadBalancerClientFactory, List.of()))
                .build();
    }
}
