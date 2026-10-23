package com.github.gaohongf.wo.mq;

/**
 * "提单人申请取消一张已受理的工单"。由 {@code WorkOrderServiceImpl#cancel} 在写入
 * {@code cancel_requested_at} 之后发出，供通知受理人使用。
 *
 * @param workOrderId 工单 id
 * @param title       工单标题
 * @param creatorId   提单人 id（发件人）
 * @param assigneeId  受理人 id（收件人，也是唯一能处理这次申请的人）
 * @param version     写入申请后的版本号，用于通知去重
 */
public record WorkOrderCancelRequestedEvent(
        Long workOrderId,
        String title,
        Long creatorId,
        Long assigneeId,
        int version) {
}
