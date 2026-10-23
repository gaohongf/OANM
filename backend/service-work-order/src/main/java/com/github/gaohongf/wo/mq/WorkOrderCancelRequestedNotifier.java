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
 * 收到提单人的取消申请后，通知当前受理人处理。
 *
 * <p>
 * 监听器形状与其它工单通知一致（{@code AFTER_COMMIT} + {@code fallbackExecution = true}）。
 * 收件人是确定的（当前受理人），不经过角色展开，所以直接用
 * {@link MessagePublisher#publishTemplate}。
 *
 * <h2>去重键</h2>
 * {@code work_order.cancel_requested:{工单}:{受理人}:{版本}}。同一张工单重复申请时，
 * 第一次写入后版本已经前进；第二次请求在服务层按幂等直接返回，不会重复发事件。
 * Kafka 重投同一条命令时版本不变，由消息模块的 {@code event_id} 唯一索引挡掉。
 */
@Slf4j
@Component
@AllArgsConstructor
public class WorkOrderCancelRequestedNotifier {

    private static final String TEMPLATE_KEY = "work_order.cancel_requested";

    private final MessagePublisher messagePublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCancelRequested(WorkOrderCancelRequestedEvent event) {
        // 受理人是自己时不会走到这里（服务层直接关闭），保留这个判断是兜底：
        // 给自己发一条“你申请取消、请你自己同意”的通知没有意义。
        if (event.assigneeId() == null || event.assigneeId().equals(event.creatorId())) {
            return;
        }

        Map<String, Object> args = new HashMap<>();
        args.put("title", event.title());

        String eventId = TEMPLATE_KEY + ":" + event.workOrderId() + ":"
                + event.assigneeId() + ":" + event.version();

        messagePublisher.publishTemplate(event.creatorId(), event.assigneeId(), TEMPLATE_KEY,
                args, String.valueOf(event.workOrderId()), eventId);

        log.debug("工单 {} 的取消申请通知已投递", event.workOrderId());
    }
}
