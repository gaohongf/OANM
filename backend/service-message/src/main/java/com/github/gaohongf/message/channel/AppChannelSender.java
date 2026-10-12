package com.github.gaohongf.message.channel;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.gaohongf.message.dao.NotificationDao;
import com.github.gaohongf.message.entity.po.NotificationEntity;
import com.github.gaohongf.message.entity.res.NotificationRes;
import com.github.gaohongf.message.mq.MessageCreateCommand;
import com.github.gaohongf.message.mq.MessageHandlerType;
import com.github.gaohongf.message.sse.NotificationSseRegistry;
import com.github.gaohongf.message.template.RenderedContent;
import com.github.gaohongf.web.util.Sse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 站内信渠道: 落库 + 推给在线的那个连接。
 *
 * <h2>顺序不能反: 先落库, 再推送</h2>
 * 库是权威, 推送只是"更快知道"。反过来的话, 一旦推送成功而落库失败（或事务还没提交）,
 * 用户会在界面上看到一条刷新后消失的消息 —— 比"晚 30 秒才看到"糟糕得多。
 *
 * <h2>重复投递是正常的, 不是错误</h2>
 * Kafka 至少一次投递, 所以同一条需求可能被消费两次。这时 {@code insert} 会撞
 * {@code uk_notifications_event_id}, 抛 {@code DuplicateKeyException} —— 那是幂等机制
 * <b>在工作</b>的证据, 所以记 debug 后直接返回, 既不重试也不再推送。
 * <p>
 * 刻意不用"先查有没有、再插"来避免这个异常: 那是一次多余的往返, 而且在"两个消费者
 * 同时处理同一条"时照样会漏（查的时候都没有, 插的时候撞上）—— 唯一索引让数据库来做
 * 这件事才是对的。
 */
@Slf4j
@Component
@AllArgsConstructor
public class AppChannelSender implements MessageChannelSender {

    /** 未读 */
    public static final int UNREAD = 0;

    private final NotificationDao notificationDao;
    private final NotificationSseRegistry sseRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public MessageHandlerType supports() {
        return MessageHandlerType.APP;
    }

    @Override
    public void send(MessageCreateCommand command, RenderedContent content) {
        NotificationEntity entity = new NotificationEntity();
        entity.setEventId(command.getEventId());
        entity.setReceiver(command.getReceiver());
        entity.setSender(command.getSender());
        entity.setChannel(MessageHandlerType.APP);
        entity.setType(command.getTemplateKey());
        entity.setTitle(content.title());
        entity.setContent(content.content());
        entity.setBizId(command.getBizId());
        entity.setReadFlag(UNREAD);
        // readTime 保持 null: 它是"读过"的证据, 不该在建的时候就有一个值。
        // 审计字段(create_time 等)由 AuditMetaObjectHandler 填 —— 消费线程上没有登录态,
        // create_by 会是 null, 这是预期的（见那个处理器的说明）。

        try {
            notificationDao.insert(entity);
        } catch (DuplicateKeyException duplicate) {
            log.debug("这条消息已经存在, 跳过（至少一次投递的正常情况）。eventId={}", command.getEventId());
            return;
        }

        // insert 之后 id 已经由雪花算法填回实体, 推送用的就是它 —— 前端因此能按 id 去重。
        push(command.getReceiver(), entity);
    }

    /**
     * 推送一条。
     * <p>
     * 这里失败<b>不影响已经落库的那一行</b>: 消息在收件箱里, 用户下一次轮询就会看到,
     * 只是少了"立刻出现"这一下。所以序列化失败也只记 ERROR, 不往外抛（抛出去会让 Kafka
     * 重试, 而重试时唯一索引会把整条消息挡在门外 —— 一次推送失败变成永久丢一条推送,
     * 反而更糟）。
     */
    private void push(Long receiver, NotificationEntity entity) {
        String json;
        try {
            // 用容器里那个 ObjectMapper 而不是 Sse.toJson: 这个实体带 LocalDateTime,
            // 而 Sse 里那个静态实例没装 JavaTimeModule（见 Sse.notification 的说明）。
            json = objectMapper.writeValueAsString(NotificationRes.from(entity));
        } catch (JsonProcessingException failure) {
            log.error("消息已落库但序列化失败, 这条推送跳过（用户下次拉取仍能看到）。id={}", entity.getId(), failure);
            return;
        }
        sseRegistry.push(receiver, Sse.notification(json));
    }
}
