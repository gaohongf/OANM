package com.github.gaohongf.auth.register;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.gaohongf.auth.client.RegistrationClient;
import com.github.gaohongf.auth.resolve.FeignUserLookupAutoConfiguration;

/**
 * 各服务（不含 service-auth）的端点自注册装配。
 *
 * <h2>service-auth 为什么不走这里</h2>
 * 它自己有 {@code LocalEndpointRegistration}：数据就在本地，扫描完直接入库，
 * 既不需要 Kafka 也不需要 Feign —— 而且它<b>没有负载均衡器</b>，
 * {@code RegistrationClient} 在它那里一调就报 "No Feign Client for loadBalancing defined"。
 * 这与阶段一 {@code UserLookup} 的处理方式一致：谁拥有数据，谁就用本地实现。
 *
 * <h2>为什么卡负载均衡器</h2>
 * 与其他 Feign 相关的自动配置同理：{@code RegistrationClient} 用 {@code lb://} 地址，
 * 而 {@code spring-cloud-starter-openfeign} 把 loadbalancer 声明成 {@code <optional>true</optional>}，
 * 不会传递过来。卡 {@link FeignClient} 等于没卡（common 会把它带给所有服务）。
 *
 * <h2>为什么卡 SERVLET</h2>
 * 需要 {@link RequestMappingHandlerMapping} 来扫描端点。service-ai 是 WebFlux，没有这个类，
 * 它的端点扫描方式也和 MVC 不同（那是另一件事）。
 */
@AutoConfiguration(after = FeignUserLookupAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(value = {FeignClient.class, KafkaTemplate.class},
        name = "org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory")
public class ServiceRegistrationAutoConfiguration {

    @Bean
    public ServiceRegistrar serviceRegistrar(RequestMappingHandlerMapping handlerMapping,
                                             KafkaTemplate<String, String> kafkaTemplate,
                                             RegistrationClient registrationClient,
                                             ObjectMapper objectMapper,
                                             Environment environment) {
        return new ServiceRegistrar(handlerMapping, kafkaTemplate, registrationClient, objectMapper,
                instanceId(environment));
    }

    /**
     * 上报方的标识，只用于日志。
     * <p>
     * 用"服务名@主机名"而不是随机 UUID：排查时想知道的是"哪个实例上报的"，
     * 而不是"哪一次启动"。拿到主机名失败也不影响功能，所以退回成 unknown 而不是报错。
     */
    private static String instanceId(Environment environment) {
        String appName = environment.getProperty("spring.application.name", "unknown-service");
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            host = "unknown-host";
        }
        return appName + "@" + host;
    }
}
