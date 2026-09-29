package com.github.gaohongf.gateway.route;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 动态路由的基础设施接线。
 */
@Configuration
@EnableScheduling
public class DynamicRouteConfiguration {

    /**
     * 刻意用 {@code @Bean} 显式声明, 而不是 {@code @EnableConfigurationProperties}。
     * <p>
     * 因为 {@link RouteVersionPoller} 的 {@code @Scheduled} 需要在这个 bean 上求值 SpEL
     * （{@code #{@gatewayRouteProperties.versionPollInterval.toMillis()}}）。用
     * {@code @EnableConfigurationProperties} 时 bean 名是
     * {@code "oanm.gateway.routes-...GatewayRouteProperties"} 这种带前缀和全限定名的串,
     * 在 SpEL 里既难写又容易在重命名包之后悄悄失效。显式声明后 bean 名就是方法名。
     */
    @Bean
    @ConfigurationProperties(prefix = "oanm.gateway.routes")
    public GatewayRouteProperties gatewayRouteProperties() {
        return new GatewayRouteProperties();
    }

    /**
     * 带负载均衡的 WebClient 构造器。
     * <p>
     * 有了 {@code @LoadBalanced}, 就能用 {@code http://service-auth/...} 这样的地址 ——
     * 由 Nacos + LoadBalancer 解析成真实实例, 不需要在配置里写死 IP。
     * service-auth 有多个实例时也会自动轮询。
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
