package com.github.gaohongf.auth.register;

import java.util.List;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.gaohongf.auth.mq.model.RegisterServiceAuth;
import com.github.gaohongf.mq.KafkaTopics;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消费各服务的端点上报，登记成权限行。
 *
 * <h2>为什么消费的是 String 而不是直接反序列化成对象</h2>
 * 用 {@code JsonDeserializer} 的话，生产端的 {@code JsonSerializer} 默认会在消息头里写上
 * 类的全限定名，消费端据此选择类型 —— 于是消息与类的包名绑死。改一次包名，broker 上
 * 积压的历史消息就再也处理不了。这里手动用 Jackson 解析，payload 只有字段名这一个契约。
 *
 * <h2>两类失败的处理方式不同</h2>
 * <ul>
 *   <li><b>解析不了</b>：重试多少次都一样。记 ERROR 后<b>吞掉</b>让消息过去 ——
 *       否则一条毒消息会把整个分区卡住，后面的正常上报全都处理不了。</li>
 *   <li><b>登记失败</b>（数据库不可用等）：抛出去交给容器的错误处理器<b>重试</b>。
 *       登记是"丢不起"的：漏一个端点，那个端点就永远没有权限行，任何角色都无法被授予它。</li>
 * </ul>
 */
@Slf4j
@Component
@AllArgsConstructor
public class ServiceRegistrationConsumer {

    /** 消费组。同一个服务上报的消息只需要被处理一次，所以用固定组名。 */
    private static final String GROUP_ID = "oanm-service-auth";

    private final ObjectMapper objectMapper;
    private final EndpointRegistrationService registrationService;

    @KafkaListener(topics = KafkaTopics.SERVICE_REGISTRATION, groupId = GROUP_ID)
    public void onRegistrationReported(String payload) {
        RegisterServiceAuth registration;
        try {
            registration = objectMapper.readValue(payload, RegisterServiceAuth.class);
        } catch (JsonProcessingException failure) {
            log.error("自注册消息无法解析，已丢弃（重试也不会变好，留着会卡住分区）。payload={}", payload, failure);
            return;
        }

        List<String> created = registrationService.register(registration.getAuths());
        if (created.isEmpty()) {
            log.debug("收到 {} 的自注册上报，{} 个端点均已登记过", registration.getInstanceId(),
                    registration.getAuths() == null ? 0 : registration.getAuths().size());
        }
        // created 非空时 EndpointRegistrationService 里已经打了 INFO
    }
}
