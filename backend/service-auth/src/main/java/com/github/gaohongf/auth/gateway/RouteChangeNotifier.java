package com.github.gaohongf.auth.gateway;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.github.gaohongf.redis.RedisChannels;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 路由变更的统一通知出口：维护版本号, 并广播给网关。
 *
 * <h2>版本号为什么放在 Redis 的计数器里</h2>
 * 不能用"最大的 update_time"之类的时间戳当版本号: {@code DATETIME} 是秒精度,
 * 同一秒内的两次修改会得到相同的版本号, 于是网关比对下来认为"没变"而<b>静默漏掉一次更新</b>。
 * Redis 的 {@code INCR} 单调递增、精确, 而且天然就是共享的（多实例 service-auth 也一致）。
 * <p>
 * Redis 重启丢了计数器也无所谓: 值从 1 重新开始, 和网关记住的旧值不同, 网关据此重拉一次。
 * 多拉一次是安全的（重拉是幂等的全量替换）, 漏拉才不安全。
 *
 * <h2>为什么既广播又留着版本号接口</h2>
 * pub/sub 是<b>发后即忘</b>: 网关重启、网络抖动期间的消息会永久丢失。所以网关除了订阅,
 * 还要定期拿 {@link #currentVersion()} 比对一次兜底。两个机制缺一不可 ——
 * 只有广播会在丢消息后长期不一致, 只有轮询则最坏要等一个轮询周期。
 *
 * <h2>发布时机</h2>
 * {@code AFTER_COMMIT}: 提交前广播会让网关拉到未提交的旧数据。理由同 {@link RouteChangedEvent}。
 */
@Slf4j
@Component
@AllArgsConstructor
public class RouteChangeNotifier {

    /** Redis 里存版本号的键。只有本服务的内部接口会读它, 网关读的是 HTTP 接口。 */
    private static final String VERSION_KEY = "oanm:routes:version";

    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate stringRedisTemplate;

    /** 路由增删改之后调用, 一定要在事务内（否则失去 after-commit 的保护）。 */
    public void changed(String routeId) {
        eventPublisher.publishEvent(new RouteChangedEvent(routeId));
    }

    /** 当前版本号。Redis 里没有时返回 {@code "0"}。 */
    public String currentVersion() {
        String value = stringRedisTemplate.opsForValue().get(VERSION_KEY);
        return value == null ? "0" : value;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRouteChanged(RouteChangedEvent event) {
        String version;
        try {
            Long bumped = stringRedisTemplate.opsForValue().increment(VERSION_KEY);
            version = bumped == null ? "0" : String.valueOf(bumped);
        } catch (Exception failure) {
            // 拿不到版本号就没法广播。数据已经落库了, 让这次操作整体失败没有意义 ——
            // 网关会退化成"直到下次有人改路由才发现变化"。这不是完美降级, 所以要 WARN 出来。
            log.warn("路由已变更(routeId={})但版本号自增失败, 网关可能无法及时感知", event.routeId(), failure);
            return;
        }

        try {
            stringRedisTemplate.convertAndSend(RedisChannels.ROUTES_CHANGED, version);
            log.debug("路由变更已广播, routeId={} version={}", event.routeId(), version);
        } catch (Exception failure) {
            // 广播失败比上面轻: 版本号已经自增, 网关的轮询兜底会发现自己落后并对齐。
            log.warn("路由变更(routeId={})广播失败, 版本号已置为 {}, 网关将在轮询时对齐",
                    event.routeId(), version, failure);
        }
    }
}
