package com.github.gaohongf.message.publish;

import java.util.Map;

/**
 * 业务服务发消息的唯一入口。
 *
 * <h2>契约：绝不抛异常</h2>
 * 所有方法的实现都必须是<b>尽力而为</b>的：失败只记日志，不抛。原因不是偷懒，而是调用它的地方
 * 都是业务主流程（例如"工单已经插库了，顺手通知一下超管"）—— 通知失败不该让建单失败，
 * 更不该让一个已经提交的事务回滚。所以这里没有返回值、没有可选失败信号，
 * 需要知道结果的话看日志（这也是刻意的：留一个 {@code boolean} 返回值会让调用方开始写
 * "通知失败就报错"的逻辑，那正是要避免的耦合）。
 *
 * <h2>为什么收件人用"具体的人"，而不是"角色"</h2>
 * Kafka 里的命令带的是具体 {@code receiver}，所以消息模块<b>消费侧的零出站依赖</b>：
 * 它只认"给谁存一条"。（反过来说，如果命令里带角色名，消费时必须回查 service-auth，
 * 于是 auth 一抖动，消息模块的消费位点就跟着抖。）角色展开由生产方做，见
 * {@link #publishToRole} —— 这个能力放在 common 里，所以仍然只有一份实现。
 *
 * @see com.github.gaohongf.message.mq.MessageCreateCommand
 */
public interface MessagePublisher {

    /**
     * 给一个收件人发一条模板站内信。
     *
     * @param sender      触发人 id；系统消息传 {@code null}
     * @param receiver    收件人 id，必填
     * @param templateKey 模板键（{@code message_templates.template_key}）；模板里用到的占位符
     *                    必须在 {@code args} 里都有值，否则渲染失败且<b>不落库</b>（见 TemplateRenderer）
     * @param args        模板占位符参数，可为空
     * @param bizId       可选业务 id（如工单 id），供前端点击后跳转
     * @param eventId     <b>幂等键，必填</b>。约定用确定性值（如 {@code work_order.created:123:7}），
     *                    这样重发同一条也是无害的
     */
    void publishTemplate(Long sender, Long receiver, String templateKey,
            Map<String, Object> args, String bizId, String eventId);

    /**
     * 给某个角色的<b>全部成员</b>各发一条。
     * <p>
     * 在这里把角色展开成收件人列表（查一次 service-auth），再逐条调用
     * {@link #publishTemplate}，所以每条消息的 {@code eventId} 是
     * {@code eventIdPrefix + ":" + 收件人id} —— 去重是针对"某人的某条消息"，不是针对这次事件。
     *
     * <h3>成员是"此刻"的快照</h3>
     * 展开发生在发布时。所以后来才拿到该角色的人看不到这条历史通知 —— 这是对的：
     * 站内信表达的是"这件事发生时你该知道"，不是"该角色的所有人都必须看到"。
     *
     * @param roleName      角色名，如 {@code admin}
     * @param eventIdPrefix 幂等键前缀，如 {@code work_order.created:123}
     */
    void publishToRole(String roleName, Long sender, String templateKey,
            Map<String, Object> args, String bizId, String eventIdPrefix);
}
