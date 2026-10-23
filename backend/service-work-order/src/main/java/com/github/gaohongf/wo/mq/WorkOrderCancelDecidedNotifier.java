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
 * 受理人同意或拒绝取消申请后，把结果告诉提单人。
 *
 * <p>
 * 同意和拒绝复用同一个监听器，但发的是<b>两个不同的模板键</b>：前端要靠
 * {@code type} 决定点击后落到哪里。同意和拒绝之后工单都仍然存在（同意后状态是
 * {@code CLOSED}），所以两种结果都可以直接打开详情。
 *
 * <p>
 * 模板键与 service-message 的种子数据一一对应，改这里必须同步改种子。
 */
@Slf4j
@Component
@AllArgsConstructor
public class WorkOrderCancelDecidedNotifier {

    private static final String APPROVED_TEMPLATE = "work_order.cancel_approved";
    private static final String REJECTED_TEMPLATE = "work_order.cancel_rejected";

    private final MessagePublisher messagePublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCancelDecided(WorkOrderCancelDecidedEvent event) {
        if (event.creatorId() == null || event.creatorId().equals(event.assigneeId())) {
            // 没有提单人（系统单据）或受理人就是提单人时不需要通知。
            // 后者正常流程不会出现：自己名下已受理的单子由服务层直接取消。
            return;
        }

        String templateKey = event.approved() ? APPROVED_TEMPLATE : REJECTED_TEMPLATE;
        Map<String, Object> args = new HashMap<>();
        args.put("title", event.title());

        String eventId = templateKey + ":" + event.workOrderId() + ":"
                + event.creatorId() + ":" + event.version();

        messagePublisher.publishTemplate(event.assigneeId(), event.creatorId(), templateKey,
                args, String.valueOf(event.workOrderId()), eventId);

        log.debug("工单 {} 的取消处理结果已通知提单人, approved={}", event.workOrderId(), event.approved());
    }
}
