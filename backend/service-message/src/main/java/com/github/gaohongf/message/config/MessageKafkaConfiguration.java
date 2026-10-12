package com.github.gaohongf.message.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import com.github.gaohongf.mq.KafkaTopics;

/**
 * 站内信话题的声明。
 *
 * <h2>为什么由消费方声明</h2>
 * 理由与 {@code RegistrationKafkaConfiguration} 相同: 不能指望 broker 开着
 * {@code auto.create.topics.enable}（生产上通常关着）, 而"需要几个分区、几个副本"
 * 是消费方最清楚的事。Spring Boot 启动时会用 {@code KafkaAdmin} 创建这里声明的 topic,
 * 已存在则跳过; 建不了也只是记日志, 不会让应用起不来。
 *
 * <h2>为什么是 1 个分区</h2>
 * 站内信的量级是"每次业务动作几条", 不需要并发消费。单分区顺带保证了<b>全局保序</b>:
 * 同一个用户的先建单通知、后受理通知必然按发生顺序落库 —— 这比"给用户按 id 排序"
 * 更根本, 因为 id 排序依赖的是雪花 id 的单调性, 而跨分区时连那个都不成立。
 * <p>
 * 消息的 key 是收件人 id（见 {@link KafkaTopics#SERVICE_CREATE_MESSAGE_APP}）,
 * 所以将来要扩分区时, 同一个用户的消息仍然落在同一分区上, 消费端不用动。
 */
@Configuration
public class MessageKafkaConfiguration {

    @Bean
    public NewTopic serviceCreateMessageAppTopic() {
        return TopicBuilder.name(KafkaTopics.SERVICE_CREATE_MESSAGE_APP)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
