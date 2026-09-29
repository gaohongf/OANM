package com.github.gaohongf.auth.satoken;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 收到 {@link AuthorityChangedEvent} 后失效本地授权缓存。
 *
 * <h2>为什么必须是 AFTER_COMMIT</h2>
 * 直觉上"改完立刻失效"最保险, 实际相反。如果失效发生在事务提交<b>之前</b>:
 * <ol>
 *   <li>本线程清掉了缓存</li>
 *   <li>另一个线程此时正好来鉴权, 它去查库 —— 但读不到本线程尚未提交的新数据,
 *       于是把<b>旧数据</b>重新缓存进去</li>
 *   <li>本线程提交</li>
 * </ol>
 * 结果缓存里留着一份旧授权, 而且要等满 30 秒 TTL 才会自己好。
 * 这比"晚几十毫秒失效"糟糕得多, 所以用 {@code AFTER_COMMIT}。
 *
 * <p>{@code fallbackExecution = true} 是为了照顾没有事务的调用场景（如启动期初始化）——
 * 那种情况下没有提交点可言, 立即执行才是对的。
 *
 * <h2>下一步在这里加广播</h2>
 * 其他服务各自持有 {@code RemoteStpInterface} 的本地缓存, 只清本进程的不够。
 * 补一个 Redis 发布即可, 本类不需要别的改动 —— 这正是把它做成独立监听者的好处。
 */
@Slf4j
@Component
@AllArgsConstructor
public class AuthorityCacheInvalidator {

    private final StpInterfaceImpl stpInterface;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuthorityChanged(AuthorityChangedEvent event) {
        if (event.isAll()) {
            stpInterface.invalidateAll();
            log.debug("授权数据变更, 已全量失效本地授权缓存");
        } else {
            stpInterface.invalidateUser(event.userId());
            log.debug("用户 {} 的授权变更, 已失效其本地缓存", event.userId());
        }
    }
}
