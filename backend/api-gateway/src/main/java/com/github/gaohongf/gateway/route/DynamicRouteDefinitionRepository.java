package com.github.gaohongf.gateway.route;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 由 service-auth 驱动的路由仓库。
 *
 * <h2>为什么自己实现一个而不是用自带的</h2>
 * 网关的 jar 里自带一个 {@code RedisRouteDefinitionRepository}（由
 * {@code spring.cloud.gateway.server.webflux.redis-route-definition-repository.enabled} 开启）。
 * 不用它的理由是: 它把<b>路由定义本身</b>存进 Redis, 于是 Redis 里多出一份权威副本 ——
 * 一旦和 MySQL 不一致, 没有任何依据判断谁对。
 * <p>
 * 这里的做法是 Redis 只当<b>失效信号</b>（一个版本号）, 数据仍然只从 service-auth 的
 * 数据库拉。Redis 里不会出现路由数据, 也就不会有副本不一致的问题。
 *
 * <h2>写入为什么直接抛异常</h2>
 * 权威来源在 service-auth, 这个仓库是只读的镜像。如果允许写入, 下一次刷新就会把它覆盖掉 ——
 * 表现为"通过 /actuator/gateway/routes 加了条路由, 过一会儿自己没了"。
 * 与其静默丢失, 不如明确拒绝并指出正确的入口。
 */
@Slf4j
@Component
public class DynamicRouteDefinitionRepository implements RouteDefinitionRepository {

    /**
     * 按 id 索引。用 {@code LinkedHashMap} 保留插入顺序: 虽然路由匹配最终按 order 排序,
     * 但顺序稳定能让日志和 /actuator/gateway/routes 的输出可复现, 排查时省事。
     */
    private final Map<String, RouteDefinition> routes = new LinkedHashMap<>();

    /**
     * 整体替换全部路由。
     * <p>
     * 必须是全量替换而不是增量合并: service-auth 返回的就是全量快照, 增量合并会让
     * <b>已删除的路由永远留在内存里</b>。
     */
    public synchronized void replaceAll(List<RouteDefinition> definitions) {
        routes.clear();
        for (RouteDefinition definition : definitions) {
            if (definition.getId() == null || definition.getId().isBlank()) {
                // 没有 id 就无法索引也无法去重。丢一条坏数据比让整个集合结构不确定好。
                log.warn("忽略一条没有 id 的路由定义: {}", definition);
                continue;
            }
            routes.put(definition.getId(), definition);
        }
        log.info("路由已更新, 当前共 {} 条: {}", routes.size(), routes.keySet());
    }

    /** 当前路由条数, 供健康检查/日志用 */
    public synchronized int size() {
        return routes.size();
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        // 快照一份再返回, 避免在遍历过程中被 refresh 修改
        List<RouteDefinition> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(routes.values());
        }
        return Flux.fromIterable(snapshot);
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return Mono.error(new UnsupportedOperationException(
                "网关的路由是只读的, 请通过 service-auth 的 /api/auth/api-routes 修改（见 DynamicRouteDefinitionRepository 的说明）"));
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return Mono.error(new UnsupportedOperationException(
                "网关的路由是只读的, 请通过 service-auth 的 /api/auth/api-routes 修改（见 DynamicRouteDefinitionRepository 的说明）"));
    }
}
