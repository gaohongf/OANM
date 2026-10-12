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
 * 建单成功后, 给超管角色的全部成员各发一条站内信。
 *
 * <h2>这是"消息模块"接的第一条真实链路</h2>
 * 它不是通用的"工单通知机制", 只做一件事: 让超管知道有人提了新工单。
 * 挑它是因为<b>收件人是确定的</b>（admin 角色）而触发点是确定的（建单成功）——
 * 排查"通知没到"时, 不确定的环节越少越好。
 *
 * <h2>为什么是 {@code AFTER_COMMIT} + {@code fallbackExecution = true}</h2>
 * <ul>
 *   <li>{@code AFTER_COMMIT}: 事务提交前发通知, 万一是回滚, 用户会收到一条"工单已提交"
 *       而库里根本没有那个工单。当前 {@code create} 上没有 {@code @Transactional}
 *       （单条插入, 本就是自动提交）, 所以这个约束现在看不出效果 —— 写成它正是为了
 *       "将来给 create 加事务时不用回来改这里"。仓里已有同样的先例（{@code RouteChangeNotifier}）。</li>
 *   <li>{@code fallbackExecution = true}: 没有事务时要照常触发。不加的话,
 *       上面那个"现在没有事务"的状态会让监听器<b>永远不被调用</b> —— 而且完全不报错,
 *       症状就是"通知功能写完了但不发"。</li>
 * </ul>
 *
 * <h2>为什么这里看不到 try/catch</h2>
 * 通知失败绝不能影响建单, 但这件事不需要在这里兜: {@link MessagePublisher} 的契约
 * 就是"绝不抛异常"（失败只记日志）, 收件人解析失败也在它的内部处理。
 * 在这里再包一层 try/catch 只会让"到底谁保证不抛"变得含糊。
 * <p>
 * 代价要说清楚: 收件人解析是一次<b>同步的</b>远程调用, 跑在建单请求线程上。
 * service-auth 抖动时会拖慢建单（有 Feign 超时兜底）, 但不会让建单失败。
 */
@Slf4j
@Component
@AllArgsConstructor
public class WorkOrderCreatedNotifier {

    /**
     * 收件角色: 超管。
     * <p>
     * 这是 {@code roles.role_name} 里的值（service-auth 的种子脚本建的）。写成常量而不是
     * 散在参数里, 是因为它是<b>配置而不是数据</b>: 哪天要改成"通知运维组", 改的是这里一行。
     */
    private static final String RECIPIENT_ROLE = "admin";

    /** 模板键, 对应 service-message 的 {@code message_templates.template_key}。 */
    private static final String TEMPLATE_KEY = "work_order.created";

    private final MessagePublisher messagePublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkOrderCreated(WorkOrderCreatedEvent event) {
        Map<String, Object> args = new HashMap<>();
        args.put("title", event.title());
        // 传中文而不是枚举的 name(): 正文是给超管看的, 不该出现 FAULT 这种代码值。
        // 由生产方翻译而不是让消息模块认识工单枚举 —— 理由见 WorkOrderType 的类注释
        // 和 service-message/db/seed.sql 里那段说明。
        args.put("typeLabel", event.type() == null ? null : event.type().label());

        // eventId 用"事件的确定性描述 + 收件人"(收件人由 publishToRole 拼上):
        // 同一个工单重复通知时, 每人仍然只会有一条 —— Kafka 至少一次投递的去重靠的就是它。
        // bizId 是工单 id, 前端点击消息后据此跳转到工单详情。
        messagePublisher.publishToRole(RECIPIENT_ROLE, event.creatorId(), TEMPLATE_KEY, args,
                String.valueOf(event.workOrderId()),
                "work_order.created:" + event.workOrderId());

        log.debug("工单 {} 的建单通知已投递", event.workOrderId());
    }
}
