package com.github.gaohongf.wo.mq;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.github.gaohongf.message.publish.MessagePublisher;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 派发成功后, 给受理人发一条站内信。
 *
 * <p>
 * 监听器形状与 {@link WorkOrderCreatedNotifier} 一致（{@code AFTER_COMMIT} +
 * {@code fallbackExecution = true}）: 没有事务时立即触发, 将来给 assign 加上事务时
 * 又不会在回滚后发出通知。
 *
 * <h2>去重键为什么带版本号</h2>
 * {@code work_order.assigned:{工单}:{受理人}:{版本}} —— 同一张单改派给同一个人时,
 * 版本号变了, 这是一条新通知; Kafka 重投同一条命令时版本号不变, 被消息模块的
 * {@code event_id} 唯一索引挡掉。只用工单 id 会把合法的改派通知也去重掉。
 */
@Slf4j
@Component
@AllArgsConstructor
public class WorkOrderAssignedNotifier {

    private static final String TEMPLATE_KEY = "work_order.assigned";

    private final MessagePublisher messagePublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkOrderAssigned(WorkOrderAssignedEvent event) {
        // 操作人就是受理人时不发: 刚点完"派给自己"的人不需要再收一条自己的动作通知。
        if (event.assigneeId().equals(event.operatorId())) {
            return;
        }

        Map<String, Object> args = new HashMap<>();
        args.put("title", event.title());
        args.put("typeLabel", event.type() == null ? null : event.type().label());

        String eventId = TEMPLATE_KEY + ":" + event.workOrderId() + ":" + event.assigneeId()
                + ":" + event.version();

        messagePublisher.publishTemplate(event.operatorId(), event.assigneeId(), TEMPLATE_KEY,
                args, String.valueOf(event.workOrderId()), eventId);

        log.debug("工单 {} 的派发通知已投递", event.workOrderId());
    }
}
