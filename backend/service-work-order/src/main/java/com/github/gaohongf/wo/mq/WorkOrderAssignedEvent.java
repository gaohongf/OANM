package com.github.gaohongf.wo.mq;

import com.github.gaohongf.wo.entity.po.WorkOrderType;

/**
 * "工单被派给某人了"。由 {@code WorkOrderServiceImpl#assign} 在更新成功后发出。
 *
 * <h2>为什么带 version</h2>
 * 通知的去重键需要能区分"同一张单的两次派发"。工单 id 不够：改派后它是同一个 id，
 * 第二次通知会被当成重复丢掉。版本号在每次更新时自增，正好是这次派发的天然序号。
 *
 * @param workOrderId 工单 id
 * @param title       工单标题
 * @param type        工单类型, 用于把中文类型名带进正文
 * @param assigneeId  受理人 id（收件人）
 * @param operatorId  操作人 id（发件人）；与受理人相同时不发送通知
 * @param version     本次更新后的版本号
 */
public record WorkOrderAssignedEvent(
        Long workOrderId,
        String title,
        WorkOrderType type,
        Long assigneeId,
        Long operatorId,
        int version) {
}
