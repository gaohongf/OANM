package com.github.gaohongf.auth.satoken;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.gaohongf.auth.client.ApiResponse;
import com.github.gaohongf.auth.client.UserClient;
import com.github.gaohongf.auth.res.UserAuthorities;

import cn.dev33.satoken.stp.StpInterface;
import lombok.extern.slf4j.Slf4j;

/**
 * 从 service-auth 取权限的 {@link StpInterface} 实现, 给"服务自己鉴权"的服务用。
 *
 * <h2>为什么必须有缓存, 而且必须在本地</h2>
 * sa-token <b>完全不缓存</b>权限列表 —— {@code StpLogic.hasPermission} 每次调用都会回调
 * {@code StpInterface.getPermissionList}（已反编译确认: 连 {@code SaSession} 里的
 * {@code PERMISSION_LIST} 常量那条路径都不读写）。没有缓存的话, 每个请求都要打一次远程调用。
 * <p>
 * 缓存放本地而不是 Redis, 是为了不引入第二个跨服务的一致性来源: 这份数据本来就是
 * "最多旧几十秒也没关系"的授权快照, 短 TTL 的本地副本足够, 而 Redis 副本又需要一套失效机制。
 *
 * <h2>取不到时一律按"无权限"处理</h2>
 * 这和 {@code UserResolveStrategy} 的取舍不同: 那边取不到用户只是显示降级, 可以放过去;
 * 这边取不到权限是<b>安全问题</b>, 必须拒绝。所以失败时返回空授权而不是抛异常或放行。
 * <p>
 * 失败<b>不写缓存</b> —— 否则 service-auth 抖一下, 这个用户就会在 TTL 内被当成无权限,
 * 表现为"刚才还能用的功能突然全 403, 过一会儿又好了"。
 *
 * <h2>覆盖方式</h2>
 * 由 {@code RemoteStpInterfaceAutoConfiguration} 用 {@code @ConditionalOnMissingBean} 装配,
 * 所以 service-auth 自己的 {@code StpInterfaceImpl}（直接查库, 不经网络）会优先生效。
 */
@Slf4j
public class RemoteStpInterface implements StpInterface, LocalAuthorityCache {

    /** 授权快照的存活时间。改角色后最迟这么久生效, 这是刻意换取"每请求零远程调用"的代价 */
    static final Duration LOCAL_TTL = Duration.ofSeconds(30);

    private static final int LOCAL_MAX_SIZE = 10_000;

    /** 取不到授权只 WARN 一次, 避免 service-auth 挂掉时日志被刷爆 */
    private static final AtomicBoolean FETCH_FAILURE_WARNED = new AtomicBoolean();

    private final UserClient userClient;

    /** Optional 包装: Caffeine 不接受 null value, 而"这个用户没有任何角色权限"是合法且常见的状态 */
    private final Cache<Long, Optional<UserAuthorities>> localCache = Caffeine.newBuilder()
            .maximumSize(LOCAL_MAX_SIZE)
            .expireAfterWrite(LOCAL_TTL)
            .recordStats()
            .build();

    public RemoteStpInterface(UserClient userClient) {
        this.userClient = userClient;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return authorities(loginId)
                .map(UserAuthorities::permissions)
                .orElseGet(List::of);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return authorities(loginId)
                .map(UserAuthorities::roles)
                .orElseGet(List::of);
    }

    /** 供观测用, 确认本地缓存真的在挡远程调用 */
    public CacheStats localCacheStats() {
        return localCache.stats();
    }

    /**
     * 失效某个用户。由 service-auth 的 Redis 广播触发 —— 见
     * {@code AuthorityBroadcastAutoConfiguration}。没有广播的话要等满 30 秒 TTL。
     */
    @Override
    public void invalidateUser(Long userId) {
        localCache.invalidate(userId);
    }

    /**
     * 全量清空。用于"影响面不确定"的变更（改了角色本身的权限、删了角色或权限）。
     * <p>
     * 这里用 {@code invalidateAll} 而不是逐个失效, 是因为无法从"某角色变了"反推出
     * 到底哪些用户受影响 —— 那需要把角色-用户关系也缓存一份, 又是一处可能不一致的状态。
     */
    @Override
    public void invalidateAll() {
        localCache.invalidateAll();
    }

    private Optional<UserAuthorities> authorities(Object loginId) {
        Long userId = toUserId(loginId);
        if (userId == null) {
            return Optional.empty();
        }

        Optional<UserAuthorities> cached = localCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }

        try {
            ApiResponse<UserAuthorities> response = userClient.findAuthorities(userId);
            if (response == null) {
                throw new IllegalStateException("查询用户授权失败: 响应为空");
            }
            // 检查信封后才能确定"这个人真的没有权限"（data 为 null）还是"这次没问到"（type=ERROR）。
            // 不检查的话后者会被负缓存 30 秒，表现为"刚才还能用的功能突然全 403，过一会儿又好了"。
            response.requireSuccess("查询用户 " + userId + " 的授权");
            Optional<UserAuthorities> result = Optional.ofNullable(response.data());
            // 只在真正拿到成功响应（哪怕是"这个用户不存在"）时才写缓存
            localCache.put(userId, result);
            return result;
        } catch (Exception failure) {
            if (FETCH_FAILURE_WARNED.compareAndSet(false, true)) {
                log.warn("获取用户授权失败, 本次按“无任何权限”处理。"
                        + "这通常意味着 service-auth 不可用或超时, 表现为所有需要权限的接口返回 403。"
                        + "后续同类失败不再重复记录。", failure);
            } else {
                log.debug("获取用户 {} 的授权失败, 按无权限处理（不写缓存）", userId, failure);
            }
            return Optional.empty();
        }
    }

    /**
     * sa-token 传进来的 loginId 是 {@code StpUtil.login()} 时的原始对象, 但从 Redis 反序列化回来
     * 可能是 Long / Integer / String 中任意一种, 所以这里宽松处理; 取不出数字就当作无权限。
     */
    private static Long toUserId(Object loginId) {
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        if (loginId instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
