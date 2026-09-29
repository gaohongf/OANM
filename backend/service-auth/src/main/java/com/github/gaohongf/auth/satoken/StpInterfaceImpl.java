package com.github.gaohongf.auth.satoken;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.gaohongf.auth.res.UserAuthorities;
import com.github.gaohongf.auth.service.UserService;

import cn.dev33.satoken.stp.StpInterface;
import lombok.extern.slf4j.Slf4j;

/**
 * service-auth 自己的 {@link StpInterface}：直接从本地库取, 不经网络。
 *
 * <h2>为什么要缓存</h2>
 * sa-token <b>完全不缓存</b>权限列表 —— {@code StpLogic.hasPermission} 每次调用都会回调这里
 * （已反编译确认）。而 {@code findAuthorities} 要跨 user_roles / role_permissions / permissions
 * 三张表 join 两次, 不缓存就是每个请求两条 join 查询。
 *
 * <h2>为什么用本地 Caffeine 而不是 @Cacheable(Redis)</h2>
 * 本地副本对"整个集群每个实例都查同一份权限"这个问题更合适: 无需失效广播, 也不会
 * 在 Redis 里留下一份需要另行维护一致性的授权副本。代价是改了某人的角色后, 本服务
 * 最迟 {@link #LOCAL_TTL} 之后才生效。
 *
 * <h2>改角色之后要主动失效</h2>
 * 阶段二加"给用户授角色 / 给角色授权"接口时, <b>必须</b>在那里调用
 * {@link #invalidateLocal(Long)}（或直接调 {@link #invalidateAll()}）,
 * 否则管理员改完权限会看到"没生效", 然后开始怀疑人生。TTL 只是兜底, 不是设计意图。
 */
@Slf4j
@Component
public class StpInterfaceImpl implements StpInterface, LocalAuthorityCache {

    /** 本地授权快照的存活时间, 同时也是"改权限后忘记失效"时的最坏生效延迟 */
    static final Duration LOCAL_TTL = Duration.ofSeconds(30);

    private static final int LOCAL_MAX_SIZE = 10_000;

    private static final AtomicBoolean DB_FAILURE_WARNED = new AtomicBoolean();

    private final UserService userService;

    /** Optional 包装: Caffeine 不接受 null, 而"这个用户没有任何角色权限"是合法且常见的状态 */
    private final Cache<Long, Optional<UserAuthorities>> localCache = Caffeine.newBuilder()
            .maximumSize(LOCAL_MAX_SIZE)
            .expireAfterWrite(LOCAL_TTL)
            .recordStats()
            .build();

    public StpInterfaceImpl(UserService userService) {
        this.userService = userService;
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

    /** 供观测用, 确认本地缓存真的在挡库查询 */
    public CacheStats localCacheStats() {
        return localCache.stats();
    }

    /** 改了某个用户的角色之后调用, 让改动立刻生效 */
    @Override
    public void invalidateUser(Long userId) {
        localCache.invalidate(userId);
    }

    /**
     * 改了角色本身（如给角色增删权限）之后调用 —— 影响所有持有该角色的人。
     * <p>
     * 也会被 Redis 广播调用: <b>service-auth 多实例</b>时, 实例 A 上管理员改了权限,
     * 实例 B 是收不到 A 的本地 Spring 事件的, 只能靠广播让 B 也失效。
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
            // 用户不存在时 findAuthorities 返回的两个集合都是空, 缓存住正好是最想要的负缓存
            Optional<UserAuthorities> result = Optional.ofNullable(userService.findAuthorities(userId));
            localCache.put(userId, result);
            return result;
        } catch (Exception failure) {
            // 查库失败按无权限处理（安全侧默认拒绝）, 且不写缓存 —— 否则一次抖动会让这个用户
            // 在 TTL 内一直被判为无权限, 表现为"功能突然全 403, 一会儿又自己好了"。
            if (DB_FAILURE_WARNED.compareAndSet(false, true)) {
                log.warn("查询用户授权失败, 本次按“无任何权限”处理。后续同类失败不再重复记录。", failure);
            } else {
                log.debug("查询用户 {} 的授权失败, 按无权限处理（不写缓存）", userId, failure);
            }
            return Optional.empty();
        }
    }

    /**
     * sa-token 传进来的 loginId 是 {@code StpUtil.login()} 时的原始对象, 但从 Redis
     * 反序列化回来可能是 Long / Integer / String 中任意一种, 所以宽松处理。
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
