package com.github.gaohongf.message.service;

import com.github.gaohongf.message.entity.req.NotificationReadState;
import com.github.gaohongf.message.entity.res.NotificationRes;
import com.github.gaohongf.message.mq.MessageCreateCommand;
import com.github.gaohongf.model.PageRes;

/**
 * 站内信: 收（消费 Kafka）、存、读、推。
 *
 * <h2>所有读方法都以 {@code receiver} 为第一参数</h2>
 * 不是风格问题, 是<b>越权防线</b>: 收件人只能来自登录态（见 {@code CurrentLoginId}）,
 * 而且每个查询/更新都把 {@code receiver} 写进 where 条件。收件人永远不能来自请求参数 ——
 * 否则把 URL 里的 id 一换就能读别人的收件箱。所以这里的签名刻意不给调用方
 * "只按消息 id 查/改"的机会。
 */
public interface NotificationService {

    /**
     * 处理一条"创建消息"的需求: 渲染模板 → 投递给对应渠道。
     * <p>
     * 三类"重试没有意义"的情况在实现内部消化（记 ERROR 后返回, 不抛）: 需求字段缺失、
     * 模板不存在或未启用、模板渲染失败（缺占位符参数）。而落库失败<b>会</b>抛出去,
     * 由 Kafka 的重试兜底。
     *
     * @param command 已反序列化的需求; 允许字段缺失（那是生产方的 bug, 不是本方法的异常）
     */
    void handle(MessageCreateCommand command);

    /**
     * 收件箱分页, 按 id 倒序（id 是雪花值, 单调递增, 等价于按时间倒序且不会有并列）。
     *
     * @param receiver  收件人, 只能是当前登录用户
     * @param readState 已读筛选, 见 {@link NotificationReadState}
     */
    PageRes<NotificationRes> page(Long receiver, long current, long size, NotificationReadState readState);

    /** 未读条数。前端铃铛上的角标。 */
    long unreadCount(Long receiver);

    /**
     * 把一条标为已读。
     * <p>
     * 幂等: 已经读过、或者这条消息根本不是他的（{@code receiver} 不匹配）时都是"零行受影响",
     * 两种情况下都不报错也不区分 —— 调用方能做的动作完全一样（刷新列表看真实状态）,
     * 而区分开只会给出一个可以用来枚举别人消息 id 的信息泄漏点。
     */
    void markRead(Long receiver, Long id);

    /**
     * 把这个人的未读全部标为已读。
     *
     * @return 受影响行数, 供调用方知道"这次清掉了多少条"（前端用来确认操作确实生效了）
     */
    int markAllRead(Long receiver);
}
