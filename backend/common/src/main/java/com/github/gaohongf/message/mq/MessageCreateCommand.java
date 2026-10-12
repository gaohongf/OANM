package com.github.gaohongf.message.mq;

import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 「创建一条消息」的需求, 由业务服务投递到
 * {@link com.github.gaohongf.mq.KafkaTopics#SERVICE_CREATE_MESSAGE_APP}。
 *
 * <h2>为什么带的是模板键而不是正文</h2>
 * 正文由 service-message 用 {@code message_templates} 里的模板渲染（{@code {占位符}} 语法）。
 * 生产方只负责说"发生了什么"（{@code templateKey} + {@code args}），"怎么措辞"由消息模块统一决定 ——
 * 否则每个生产方各写一份文案，改一次措辞要改 N 个服务并重新发版。
 * <p>
 * 渲染结果会作为<b>快照</b>落库，所以事后改模板不会让用户看到的历史消息变样。
 *
 * <h2>为什么没有无参构造就会炸</h2>
 * 消费端是手动 Jackson 解析（见 {@code MessageCreateCommand} 的使用方）。理论上编译开了
 * {@code -parameters} 之后 Jackson 也能用带参构造，但那是"依赖编译器开关"的隐式契约：
 * 哪天有人关掉它就变成运行时才发现的失败。这里显式给 {@code @NoArgsConstructor}。
 *
 */
@Data
@NoArgsConstructor
public class MessageCreateCommand {

    /**
     * 幂等键, <b>不能为空</b>。
     * <p>
     * Kafka 是至少一次投递（消费位点提交与落库之间崩溃就会重投），所以收件箱必须能去重，
     * 否则用户会看到两条一样的未读。约定用<b>确定性</b>的值（如
     * {@code work_order.created:123:7}），这样生产方重发同一条也是无害的。
     * <p>
     * 缺了它去重会<b>静默失效</b>（MySQL 的唯一索引允许多个 NULL），所以发布器把它当硬性前置条件。
     */
    private String eventId;

    /** 触发人 id。系统消息为空。 */
    private Long sender;

    /** 收件人 id。由生产方解析（"给某个角色的人发"在生产方展开成多条命令，见 MessagePublisher）。 */
    private Long receiver;

    /** 渠道。目前只有 {@link MessageHandlerType#APP}（站内信）有实现。 */
    private MessageHandlerType handlerType;

    /** 命令产生时间（毫秒时间戳）。 */
    private Long messageCreateAt;

    /** 模板键，对应 {@code message_templates.template_key}。 */
    private String templateKey;

    /** 模板占位符的参数。模板里出现而这里没给，渲染会失败并且<b>不落库</b>（见 TemplateRenderer）。 */
    private Map<String, Object> args;

    /**
     * 可选的业务 id（如工单 id），供前端点击消息后跳转。
     * <p>
     * 单独一个字段而不是塞进 {@code args}：{@code args} 是"文案参数"，这个是"程序要用的东西"。
     * 混在一起的话，模板作者改文案时可能把一个要给前端读的值当成展示文本用掉。
     */
    private String bizId;
}
