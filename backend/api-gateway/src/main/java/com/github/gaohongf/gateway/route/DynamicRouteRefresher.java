package com.github.gaohongf.gateway.route;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 路由刷新的唯一入口：拉取 → 替换内存 → 落盘 → 通知网关重建路由。
 *
 * <h2>三个触发点</h2>
 * <ol>
 *   <li><b>启动</b>（{@link ApplicationReadyEvent}）—— 否则网关起来时没有任何路由</li>
 *   <li><b>Redis 广播</b>（见 {@code RouteBroadcastConfiguration}）—— 管理端一改就生效</li>
 *   <li><b>兜底轮询</b>（见 {@code RouteVersionPoller}）—— pub/sub 丢消息时对齐</li>
 * </ol>
 * 三者都收敛到 {@link #refresh(String)}, 保证行为一致。
 *
 * <h2>为什么可以用 block()</h2>
 * 网关是 WebFlux 应用, 在事件循环线程上 block 会死锁。但调用本类的三个线程分别是
 * 主线程（启动事件）、Redis 监听容器自己的线程池、Spring 的调度线程池 ——
 * <b>都不是 Netty 的事件循环线程</b>, 所以阻塞是安全的。加超时是为了避免 service-auth
 * 卡住时把调度线程也占死。
 */
@Slf4j
@Component
public class DynamicRouteRefresher {

    /** 单次拉取的上限。超过就当作失败, 保留当前路由。 */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);

    private final AuthRouteClient authRouteClient;
    private final DynamicRouteDefinitionRepository repository;
    private final RouteCacheStore cacheStore;
    private final ApplicationEventPublisher eventPublisher;

    public DynamicRouteRefresher(AuthRouteClient authRouteClient,
                                 DynamicRouteDefinitionRepository repository,
                                 RouteCacheStore cacheStore,
                                 ApplicationEventPublisher eventPublisher) {
        this.authRouteClient = authRouteClient;
        this.repository = repository;
        this.cacheStore = cacheStore;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 启动时准备路由。
     * <p>
     * 顺序是"先拉权威来源, 拉不到才用缓存"。反过来（先用缓存再异步拉）会让启动后的
     * 一小段时间里用着可能过期的路由, 而这段时间正好是最容易被注意到的。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (refresh("启动")) {
            return;
        }

        List<RouteDefinition> cached = cacheStore.load();
        if (cached.isEmpty()) {
            // 既拉不到又没有缓存 —— 网关此刻没有任何路由, 所有请求都会 404。
            // 这不是"降级"而是彻底不可用, 所以要 ERROR 级并说清楚怎么办。
            log.error("无法从 service-auth 拉取路由, 且本地缓存中没有可用备份 —— "
                    + "网关当前没有任何路由, 所有请求都会返回 404。"
                    + "请检查 service-auth 是否可用, 或确认缓存文件路径配置正确。");
            return;
        }

        repository.replaceAll(cached);
        publishRoutesChanged();
        log.warn("从 service-auth 拉取失败, 已回退到本地缓存中的 {} 条路由。"
                + "它们可能不是最新的, 但比没有路由好。", cached.size());
    }

    /**
     * 从 service-auth 拉全量路由并应用。
     * <p>
     * 失败时<b>保留当前路由</b>而不是清空 —— 一次网络抖动不该让网关失去全部路由。
     *
     * @param reason 触发原因, 仅用于日志（"启动" / "广播" / "轮询"）
     * @return 是否成功
     */
    public boolean refresh(String reason) {
        try {
            List<RouteDefinition> routes = authRouteClient.fetchRoutes().block(FETCH_TIMEOUT);
            if (routes == null) {
                routes = List.of();
            }
            repository.replaceAll(routes);
            // 先落盘再广播: 万一广播过程中进程挂了, 缓存里也是新的
            cacheStore.save(routes);
            publishRoutesChanged();
            log.info("路由已刷新({}), 共 {} 条", reason, routes.size());
            return true;
        } catch (Exception failure) {
            log.warn("刷新路由失败({}), 继续使用当前内存中的 {} 条路由",
                    reason, repository.size(), failure);
            return false;
        }
    }

    /**
     * 通知 Spring Cloud Gateway 重建路由。
     * <p>
     * 必须发这个事件: 路由被两层缓存挡着 —— {@code CachingRouteDefinitionLocator}
     * （缓存 RouteDefinition）与 {@code CachingRouteLocator}（缓存 Route）——
     * 两者都监听 {@code RefreshRoutesEvent}。只改内存里的仓库而不发事件, 网关会
     * <b>继续用旧路由</b>, 表现为"数据改了但转发目标没变"。
     */
    private void publishRoutesChanged() {
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
    }
}
