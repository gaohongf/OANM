package com.github.gaohongf.auth.register;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import com.github.gaohongf.mq.KafkaTopics;

/**
 * 自注册话题的声明。
 *
 * <h2>为什么要显式建 topic</h2>
 * 不能指望 broker 的 {@code auto.create.topics.enable} 是开着的（生产上通常关掉，
 * 因为自动建出来的 topic 分区数和副本数都是默认值，往往不合要求）。
 * 由消费方声明是合适的：谁消费谁清楚需要什么样的 topic。
 * <p>
 * Spring Boot 会在启动时通过 {@code KafkaAdmin} 创建这里声明的 topic（已存在则跳过）。
 * 创建失败不会让应用启动失败，只是记日志 —— 因为申请权在运维手里时，应用不该因为
 * 建不了 topic 就起不来。
 */
@Configuration
public class RegistrationKafkaConfiguration {

    @Bean
    public NewTopic serviceRegistrationTopic() {
        return TopicBuilder.name(KafkaTopics.SERVICE_REGISTRATION)
                // 1 个分区: 上报量极小（每次服务启动一批），不需要并发消费，
                // 单分区还能保证处理顺序与上报顺序一致
                .partitions(1)
                .replicas(1)
                .build();
    }
}
