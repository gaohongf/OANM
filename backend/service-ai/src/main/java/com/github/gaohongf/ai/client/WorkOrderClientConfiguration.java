package com.github.gaohongf.ai.client;

import java.util.List;

import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 调用工单服务的客户端配置。
 *
 * <p>
 * {@code service-ai} 是 WebFlux 服务，common 里的 Feign 只会启用
 * {@code com.github.gaohongf.auth.client} 下的客户端，所以这里单独提供一个
 * 带负载均衡的 {@link WebClient}，走 Nacos 里的 {@code service-work-order}。
 *
 * <h2>为什么不能注册 {@code @LoadBalanced WebClient.Builder}</h2>
 * 那样会顶掉 Spring Boot 自动配置的 {@code WebClient.Builder}（它标注了
 * {@code @ConditionalOnMissingBean}），而 Spring AI 的流式对话客户端同样从容器里取
 * {@code WebClient.Builder}。拿到负载均衡版之后，它会把 {@code api.deepseek.com}
 * 当成服务名去注册中心找实例，找不到就返回 503 —— 表现为所有流式模型调用失败，
 * 而非流式调用（走 RestClient）却正常。
 *
 * <p>
 * 所以这里只产出一个作用范围明确的 {@code WebClient}：Builder 仍用 Boot 自动配置的
 * 原型 bean，负载均衡过滤器只挂在这个客户端上，不会影响其他组件。
 */
@Configuration
public class WorkOrderClientConfiguration {

    private static final String WORK_ORDER_SERVICE = "http://service-work-order";

    @Bean
    public WebClient workOrderWebClient(WebClient.Builder builder,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        return builder
                .baseUrl(WORK_ORDER_SERVICE)
                // 第二个参数是请求转换器列表；这里不需要任何转换，服务名由 baseUrl 提供
                .filter(new ReactorLoadBalancerExchangeFilterFunction(
                        loadBalancerClientFactory, List.of()))
                .build();
    }
}
