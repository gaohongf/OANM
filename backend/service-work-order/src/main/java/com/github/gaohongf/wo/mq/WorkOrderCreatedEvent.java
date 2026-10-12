package com.github.gaohongf.wo.mq;

import com.github.gaohongf.wo.entity.po.WorkOrderType;

/**
 * "工单建好了"。由 {@code WorkOrderServiceImpl} 在插入之后发出, 供通知等旁路逻辑消费。
 *
 * <h2>为什么用 Spring 事件而不是直接调发布器</h2>
 * 建单这件事的<b>本体</b>是"插一行工单", 通知只是它的一个副作用。中间隔一个事件之后:
 * <ul>
 *   <li>建单的代码不需要认识消息模块（也就不需要在建单的单测里 mock 一个 Kafka）;</li>
 *   <li>将来加第二个副作用（比如推送给 AI 做分类）时, 建单那边一行都不用动;</li>
 *   <li>发布时机可以交给监听器的 {@code AFTER_COMMIT} 决定 —— 事务回滚时不通知,
 *       这件事只有监听器需要关心。</li>
 * </ul>
 *
 * <h2>为什么带的是 id 而不是整个实体</h2>
 * 事件是"发生了什么"的陈述, 而实体是"某一刻的数据快照"。带实体的话, 监听器很容易
 * 开始依赖实体里那些和这件事无关的字段, 于是这个事件的形状会随着工单表的列一起长。
 * 这里只带通知真正要用的四样东西。
 *
 * @param workOrderId 工单 id（雪花值, 插入时已生成）
 * @param title       工单标题
 * @param type        工单类型, 见 {@link WorkOrderType}
 * @param creatorId   建单人 id, 由审计字段填充（取不到登录态时为 null, 那时这条通知按系统消息发）
 */
public record WorkOrderCreatedEvent(Long workOrderId, String title, WorkOrderType type, Long creatorId) {
}
