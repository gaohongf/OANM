package com.github.gaohongf.auth.satoken;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.github.gaohongf.redis.RedisChannels;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 把授权变更广播给其他服务, 让它们的本地缓存也立刻失效。
 *
 * <h2>没有它会怎样</h2>
 * 其他服务各自持有 30 秒 TTL 的授权缓存, 管理员改完权限后要等它们各自过期才生效 ——
 * 表现为"改了没生效, 过一会儿又自己好了"。
 *
 * <h2>和 {@link AuthorityCacheInvalidator} 的分工</h2>
 * 两者监听同一个事件、都在事务提交后执行:
 * <ul>
 *   <li>{@code AuthorityCacheInvalidator}: 清<b>本进程</b>的缓存</li>
 *   <li>本类: 通知<b>别的进程</b>去清各自的缓存（含 service-auth 的其他实例）</li>
 * </ul>
 * 分成两个类是为了让"本地"和"远程"两条路径能各自被读懂、各自被替换, 而不是混在一个方法里。
 *
 * <h2>发布时机</h2>
 * 与本地失效同理, 必须是 {@code AFTER_COMMIT}: 如果在提交前广播, 别的服务会立刻回源查库,
 * 而那时新数据还没提交, 于是把<b>旧数据</b>重新缓存起来 —— 比不广播更糟。
 */
@Slf4j
@Component
@AllArgsConstructor
public class AuthorityBroadcaster {

    /** 载荷里表示"影响面不确定, 全量失效"的标记 */
    private static final String ALL = "*";

    private final StringRedisTemplate stringRedisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuthorityChanged(AuthorityChangedEvent event) {
        String payload = event.isAll() ? ALL : String.valueOf(event.userId());
        try {
            stringRedisTemplate.convertAndSend(RedisChannels.AUTHORITIES_CHANGED, payload);
        } catch (Exception failure) {
            // 广播失败不能让这次授权操作整体失败 —— 数据已经落库且提交了, 抛弃它毫无意义。
            // 代价是其他服务要多等各自的 TTL（最多 30 秒）才生效, 这是可以接受的降级。
            log.warn("授权变更广播发送失败, 其他服务将在其缓存 TTL 过期后才生效。载荷={}", payload, failure);
        }
    }
}
