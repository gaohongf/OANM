package com.github.gaohongf.message.publish;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.gaohongf.auth.client.UserClient;

/**
 * 各服务"发消息"能力的装配。
 *
 * <h2>为什么卡负载均衡器而不是 {@link FeignClient}</h2>
 * 与 {@code ServiceRegistrationAutoConfiguration} 同理，也与所有 Feign 相关的自动配置同一条约束：
 * {@code spring-cloud-starter-openfeign} 把 loadbalancer 声明成 {@code <optional>true</optional>}，
 * 不会传递过来，而 {@link UserClient} 用的是 {@code lb://} 地址 —— 没有负载均衡器时
 * 第一次调用就炸 {@code "No Feign Client for loadBalancing defined"}。
 * common 会把 openfeign 带给所有服务，所以只卡 {@code FeignClient} 类等于没卡。
 *
 * <h2>没有负载均衡器的服务拿不到这个 bean（刻意的）</h2>
 * 例如 service-message 自己：它只消费不生产，也不需要按角色解析收件人。这种服务注入
 * {@link MessagePublisher} 会在启动期直接失败并说"没有这个 bean"，比给一个"调了才发现
 * 少了依赖"的实现要好。要用就补一个 {@code spring-cloud-starter-loadbalancer}。
 *
 * <h2>为什么要看着 Kafka</h2>
 * {@link KafkaMessagePublisher} 依赖 {@code KafkaTemplate}。没有它的话这个类在编译期就不成立
 * （common 自己带 spring-kafka，但业务服务未必）。
 */
@AutoConfiguration
@ConditionalOnClass(value = { FeignClient.class, KafkaTemplate.class },
        name = "org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory")
public class MessagePublishAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessagePublisher.class)
    public MessagePublisher messagePublisher(KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper, UserClient userClient) {
        return new KafkaMessagePublisher(kafkaTemplate, objectMapper, userClient);
    }
}
