package com.github.gaohongf.message.mq;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.gaohongf.message.service.NotificationService;
import com.github.gaohongf.mq.KafkaTopics;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消费"创建一条站内信"的需求。
 *
 * <h2>为什么消费 String 而不是直接反序列化成对象</h2>
 * 与 {@code ServiceRegistrationConsumer} 完全一致的理由: 用 {@code JsonDeserializer} 的话,
 * 生产端的 {@code JsonSerializer} 默认会把类的全限定名写进消息头, 消费端据此选类型 ——
 * 于是消息与<b>包名</b>绑死, 改一次包名, broker 上积压的历史消息就再也处理不了。
 * 这里手动 Jackson 解析, payload 与消息之间只有字段名这一个契约。
 *
 * <h2>为什么跑在能阻塞的线程上</h2>
 * {@code @KafkaListener} 的方法由 {@code KafkaMessageListenerContainer} 自己的容器线程调用
 * （不是 reactor 的事件循环）, 所以下面这条链上的 JDBC 阻塞调用是安全的 ——
 * 这与 WebFlux 控制器里的情形正好相反（那边靠 {@code boundedElastic}）。
 *
 * <h2>三类失败的处理方式</h2>
 * <ul>
 *   <li><b>解析不了</b>: 重试多少次都一样。记 ERROR 后<b>吞掉</b>让消息过去 ——
 *       否则一条毒消息会把整个分区卡住, 后面的正常通知全都发不出去。</li>
 *   <li><b>模板缺失 / 渲染失败 / 本就是重复投递</b>: 在 {@code NotificationService} 内部
 *       消化掉（前两者记 ERROR 且不落库, 后者是正常情况）。见那两处的说明。</li>
 *   <li><b>落库失败</b>（数据库不可用等）: 抛出去交给容器的错误处理器<b>重试</b>
 *       （默认重试 9 次后记日志跳过）。站内信是"丢了就没了"的, 不能静默吞掉。</li>
 * </ul>
 */
@Slf4j
@Component
@AllArgsConstructor
public class MessageCreateConsumer {

    /**
     * 消费组。
     * <p>
     * 固定组名（不带实例后缀）: 一条"创建消息"的需求只该被处理一次, 多个实例是
     * 同一消费组内的分区竞争关系。这与自注册那个消费者是同一条约定。
     */
    private static final String GROUP_ID = "oanm-service-message";

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopics.SERVICE_CREATE_MESSAGE_APP, groupId = GROUP_ID)
    public void onCreateRequested(String payload) {
        MessageCreateCommand command;
        try {
            command = objectMapper.readValue(payload, MessageCreateCommand.class);
        } catch (JsonProcessingException failure) {
            log.error("消息需求无法解析, 已丢弃（重试也不会变好, 留着会卡住分区）。payload={}", payload, failure);
            return;
        }

        notificationService.handle(command);

        // messageCreateAt 唯一的用处就是这里: 入队到落库隔了多久。
        // 排查"通知怎么这么晚才来"时, 这个数字能一眼分清是生产端慢、broker 堵,
        // 还是消费端积压（消费延迟会随时间单调增长, 前两者不会）。
        if (command.getMessageCreateAt() != null) {
            log.debug("消息需求处理完成。eventId={} receiver={} 入队到消费 {} ms",
                    command.getEventId(), command.getReceiver(),
                    System.currentTimeMillis() - command.getMessageCreateAt());
        }
    }
}
