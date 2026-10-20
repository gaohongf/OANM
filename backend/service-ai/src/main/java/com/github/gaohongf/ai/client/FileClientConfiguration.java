package com.github.gaohongf.ai.client;

import java.util.List;

import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 读取 service-files 图片的客户端配置。
 *
 * <p>
 * 与 {@link WorkOrderClientConfiguration} 同样的约束：负载均衡过滤器只挂在这个专用
 * {@code WebClient} 上，不能注册全局 {@code @LoadBalanced WebClient.Builder}，
 * 否则 Spring AI 调用 deepseek 时也会把域名当成服务名去注册中心找实例。
 */
@Configuration
public class FileClientConfiguration {

    private static final String FILE_SERVICE = "http://service-files";

    /**
     * WebClient 默认只缓冲 256KB，一张截图就会触发
     * {@code DataBufferLimitException}。前端单张上限是 10MB，这里留出余量。
     */
    private static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;

    @Bean
    public WebClient fileWebClient(WebClient.Builder builder,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        return builder
                .baseUrl(FILE_SERVICE)
                .filter(new ReactorLoadBalancerExchangeFilterFunction(
                        loadBalancerClientFactory, List.of()))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IMAGE_BYTES))
                .build();
    }
}
