package com.github.gaohongf.message.publish;

import java.util.List;
import java.util.Map;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.gaohongf.auth.client.ApiResponse;
import com.github.gaohongf.auth.client.UserClient;
import com.github.gaohongf.message.mq.MessageCreateCommand;
import com.github.gaohongf.message.mq.MessageHandlerType;
import com.github.gaohongf.mq.KafkaTopics;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link MessagePublisher} 的 Kafka 实现。
 *
 * @see MessagePublisher 契约（绝不抛异常）与"收件人在生产方解析"的理由
 */
@Slf4j
public class KafkaMessagePublisher implements MessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final UserClient userClient;

    public KafkaMessagePublisher(KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper, UserClient userClient) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.userClient = userClient;
    }

    @Override
    public void publishTemplate(Long sender, Long receiver, String templateKey,
            Map<String, Object> args, String bizId, String eventId) {
        if (receiver == null) {
            log.error("消息没有收件人, 已丢弃。templateKey={} eventId={}", templateKey, eventId);
            return;
        }
        // 幂等键缺失必须当场拒绝, 不能"照发但没键": 库里那条唯一索引对 NULL 不生效,
        // 于是重复投递会静默产生两条一样的未读 —— 症状是"用户偶尔收到两条一样的通知",
        // 而没有任何一处报错。宁可这条通知不发, 也不要发出一个查不出原因的重复问题。
        if (!StringUtils.hasText(eventId)) {
            log.error("消息缺少幂等键(eventId), 已丢弃。templateKey={} receiver={}", templateKey, receiver);
            return;
        }

        MessageCreateCommand command = new MessageCreateCommand();
        command.setEventId(eventId);
        command.setSender(sender);
        command.setReceiver(receiver);
        // 目前只有站内信有实现; EMAIL 的占位见 service-message 的渠道分发
        command.setHandlerType(MessageHandlerType.APP);
        command.setMessageCreateAt(System.currentTimeMillis());
        command.setTemplateKey(templateKey);
        command.setArgs(args == null ? Map.of() : args);
        command.setBizId(bizId);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException failure) {
            log.error("消息序列化失败, 已丢弃。templateKey={} receiver={}", templateKey, receiver, failure);
            return;
        }

        // 刻意不 .get() 等发送结果: 这里跑在业务请求线程上, 等 Kafka 的确认会把业务响应拖住,
        // 而 Kafka 不可用时还能拖满 send 的超时时间。用 key = 收件人, 让同一个用户的消息
        // 落在同一分区上（顺序无关紧要，但将来加分片时这个选择仍然成立）。
        // 发送失败只记日志 —— 见 MessagePublisher 的契约。
        try {
            kafkaTemplate.send(KafkaTopics.SERVICE_CREATE_MESSAGE_APP, String.valueOf(receiver), payload)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.warn("消息投递到 Kafka 失败。eventId={} receiver={}", eventId, receiver, failure);
                        } else {
                            log.debug("消息已投递。eventId={} receiver={}", eventId, receiver);
                        }
                    });
        } catch (RuntimeException failure) {
            // 生产者没建好、broker 地址没配等情况在 send 当场就会抛（不是异步失败）。
            // 同样只记日志: 业务已经做完了。
            log.warn("消息投递到 Kafka 失败（同步抛出）。eventId={} receiver={}", eventId, receiver, failure);
        }
    }

    @Override
    public void publishToRole(String roleName, Long sender, String templateKey,
            Map<String, Object> args, String bizId, String eventIdPrefix) {
        List<Long> receivers;
        try {
            ApiResponse<List<Long>> response = userClient.findUserIdsByRole(roleName);
            if (response == null) {
                throw new IllegalStateException("响应为空");
            }
            // 不 requireSuccess 的话, 一次业务错误会被当成"这个角色没有成员"而静默不发,
            // 与"没问到"完全是两件事（同 UserLookup 契约里的分工）。
            response.requireSuccess("按角色查用户");
            receivers = response.data() == null ? List.of() : response.data();
        } catch (Exception failure) {
            // 收件人查不到 ⇒ 这次通知丢了, 而且是在发布端丢的（消息根本没进 Kafka）。
            // 这是刻意的取舍: 消息模块因此不必依赖 service-auth 的可用性, 代价是
            // auth 抖动期间的通知发不出去。要改成"丢给 Kafka 稍后再解析收件人"，
            // 就得让消费侧承担那次远程调用 —— 那正是这里要避免的。
            log.warn("查角色 [{}] 的成员失败, 本次通知未发送。templateKey={}", roleName, templateKey, failure);
            return;
        }

        for (Long receiver : receivers) {
            publishTemplate(sender, receiver, templateKey, args, bizId,
                    eventIdPrefix + ":" + receiver);
        }
        log.debug("已向角色 [{}] 的 {} 个成员投递模板 [{}]", roleName, receivers.size(), templateKey);
    }
}
