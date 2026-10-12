package com.github.gaohongf.message.channel;

import com.github.gaohongf.message.mq.MessageCreateCommand;
import com.github.gaohongf.message.mq.MessageHandlerType;
import com.github.gaohongf.message.template.RenderedContent;

/**
 * 一个投递渠道: "把这条已经渲染好的消息送到这个人面前"。
 *
 * <h2>为什么渠道要做成策略</h2>
 * 站内信和邮件的差别不在"怎么渲染"（那是 {@code TemplateRenderer} 的事, 两者共用）,
 * 而全在"送到哪里": 一个落库 + 推流, 一个发 SMTP。把这半段分开, 加渠道时新写一个实现类就够了,
 * 消费、渲染、幂等这些已经成立的东西一行都不用动 —— 这也是本轮把 EMAIL 留成占位却仍然
 * 值得先把这个接口立起来的原因。
 *
 * <h2>契约: 失败只记日志, 不抛</h2>
 * 与 {@code MessagePublisher} 相反: 那边是"业务主流程顺手通知, 失败不能影响业务",
 * 这边是"消费一条消息"本身 —— 落库失败<b>应该</b>抛出去让 Kafka 重试
 * （见 {@code MessageCreateConsumer} 对三类失败的划分）, 所以异常是会往外走的。
 * 只有"重试没有意义"的失败（重复投递、渠道未实现）才在实现内部消化掉。
 */
public interface MessageChannelSender {

    /** 本实现负责的渠道。分发靠它匹配, 所以同一个渠道不能有两个实现。 */
    MessageHandlerType supports();

    /**
     * 投递。
     *
     * @param command 原始需求, 渠道实现要用它的 {@code receiver}（送到谁）、
     *                {@code eventId}（幂等键）、{@code sender} / {@code bizId}（存哪去）
     * @param content 已经渲染好的标题与正文, 见 {@link RenderedContent}
     */
    void send(MessageCreateCommand command, RenderedContent content);
}
