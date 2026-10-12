package com.github.gaohongf.message.channel;

import org.springframework.stereotype.Component;

import com.github.gaohongf.message.mq.MessageCreateCommand;
import com.github.gaohongf.message.mq.MessageHandlerType;
import com.github.gaohongf.message.template.RenderedContent;

import lombok.extern.slf4j.Slf4j;

/**
 * 邮件渠道 —— <b>占位实现, 本轮刻意不发信</b>。
 *
 * <h2>为什么留一个什么都不做的实现, 而不是干脆不写</h2>
 * 没有它的话, {@code MessageHandlerType.EMAIL} 会走到"没有渠道实现这个枚举值"的分支,
 * 那里的日志是"配置错误"（有人加了枚举但没加实现）。而现在的真实状态是
 * "邮件还没接"—— 两者需要区分开, 否则将来接邮件的人会在日志里看到一堆
 * 已经存在很久、其实毫无信息量的错误。
 * <p>
 * 真正接入时, 这个类里要补的是: 收件人邮箱（现在协议里只有 userId, 得回源查一次）、
 * 模板的 EMAIL 那一份措辞（{@code message_templates} 的唯一键里带了 channel,
 * 表结构不用动）、以及发送失败的重试策略（SMTP 是外部依赖, 不能像站内信那样
 * 靠 Kafka 的重试兜底 —— 那条路会重复发信）。
 */
@Slf4j
@Component
public class EmailChannelSender implements MessageChannelSender {

    @Override
    public MessageHandlerType supports() {
        return MessageHandlerType.EMAIL;
    }

    @Override
    public void send(MessageCreateCommand command, RenderedContent content) {
        log.warn("邮件渠道尚未实现, 这条消息未发出（已渲染内容未落库）。eventId={} receiver={} title={}",
                command.getEventId(), command.getReceiver(), content.title());
    }
}
