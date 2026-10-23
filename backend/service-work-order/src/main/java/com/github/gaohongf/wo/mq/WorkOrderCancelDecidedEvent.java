package com.github.gaohongf.wo.mq;

/**
 * "受理人处理了取消申请"。由 {@code WorkOrderServiceImpl#decideCancel} 在同意或拒绝之后发出。
 *
 * @param workOrderId 工单 id
 * @param title       工单标题
 * @param creatorId   提单人 id（收件人）
 * @param assigneeId  受理人 id（发件人）
 * @param approved    true=同意取消（工单已关闭）；false=拒绝，工单继续处理
 * @param version     处理后的版本号，用于通知去重
 */
public record WorkOrderCancelDecidedEvent(
        Long workOrderId,
        String title,
        Long creatorId,
        Long assigneeId,
        boolean approved,
        int version) {
}
