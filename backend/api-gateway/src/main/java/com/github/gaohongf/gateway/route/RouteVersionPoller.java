package com.github.gaohongf.gateway.route;

import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 兜底轮询：定期比对路由版本号, 发现落后就重拉。
 *
 * <h2>为什么广播之外还需要它</h2>
 * Redis pub/sub 是<b>发后即忘</b>的, 没有持久化也没有重投:
 * <ul>
 *   <li>网关重启期间发生的变更, 它收不到（现在靠启动时拉一次覆盖住了）</li>
 *   <li>网络抖动、Redis 主从切换期间的消息会丢</li>
 *   <li>service-auth 那侧的广播本身也可能失败（见 {@code RouteChangeNotifier}, 那里只 WARN）</li>
 * </ul>
 * 没有这个轮询, 上述任何一种都会让网关<b>长期</b>停留在旧路由上, 直到下次有人改路由。
 * 有了它, 最坏也就是多等一个轮询周期。
 *
 * <h2>为什么比的是版本号而不是直接重拉</h2>
 * 重拉是一次完整的 HTTP + JSON 解析。轮询大多数时候应该什么都不做, 所以先拿一个
 * 便宜的数字比对, 只有确实落后了才去拉全量。
 */
@Slf4j
@Component
public class RouteVersionPoller {

    /** 单次取版本号的上限 */
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(3);

    private final AuthRouteClient authRouteClient;
    private final DynamicRouteRefresher refresher;

    /**
     * 上次见到的版本号。
     * <p>
     * 启动后第一次轮询时是 {@code null}, 于是必然触发一次刷新 —— 这是一次多余的拉取
     * （启动时已经拉过一次）, 但代价很小, 换来的是代码里不需要"启动时把版本号传进来"
     * 这条额外链路。
     */
    private volatile String lastSeenVersion;

    public RouteVersionPoller(AuthRouteClient authRouteClient, DynamicRouteRefresher refresher) {
        this.authRouteClient = authRouteClient;
        this.refresher = refresher;
    }

    @Scheduled(fixedDelayString = "#{@gatewayRouteProperties.versionPollInterval.toMillis()}")
    public void pollVersion() {
        String version;
        try {
            version = authRouteClient.fetchVersion().block(VERSION_TIMEOUT);
        } catch (Exception failure) {
            // 取不到版本号什么都不做。这是正常的瞬态故障, 用 debug 级别 ——
            // 用 WARN 会在 service-auth 短暂不可用时刷屏, 而此时日志里已经有别的告警了。
            log.debug("轮询路由版本号失败, 本轮跳过", failure);
            return;
        }

        if (version == null || version.equals(lastSeenVersion)) {
            return;
        }

        log.info("轮询发现路由版本变化（{} -> {}）, 重新拉取", lastSeenVersion, version);
        // 先刷新再记版本: 如果刷新失败, 下一轮还会因为版本号不一致而重试,
        // 这正是想要的。反过来先记版本会让一次失败被永久跳过。
        if (refresher.refresh("轮询")) {
            lastSeenVersion = version;
        }
    }
}
