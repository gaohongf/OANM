package com.github.gaohongf.auth.resolve;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.gaohongf.auth.res.UserRes;
import com.github.gaohongf.serializer.ResolveStrategy;
import com.github.gaohongf.serializer.annotation.User;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户字段的解析策略：本地 L1（Caffeine）→ {@link UserLookup} 回源。
 * <p>
 * 策略本身只管缓存和降级, 怎么取数交给 {@link UserLookup} —— 普通服务走 Feign 打回
 * service-auth, service-auth 自己直接查库。见 {@link UserLookup} 的说明。
 *
 * <h2>为什么本地要缓存</h2>
 * 一个列表响应里往往有几十个 {@code @User} 字段却只有个位数的 distinct user,
 * L1 命中后远程调用次数远小于字段数。没有 L1 时, 这些字段会退化成几十次 HTTP 往返。
 *
 * <h2>为什么这里不写 Redis</h2>
 * Redis 那份用户缓存由 service-auth 独占写入和失效。调用方若也往同一个 key 里写,
 * 就得和 auth 严格同步 key 形态、value 序列化格式、失效时机三件事, 任何一方改动
 * 都会静默产生脏数据。这里只持有一份 TTL 很短的本地副本, 代价是 auth 改了用户之后
 * 其他服务最多 stale {@link #LOCAL_TTL} 那么久。
 *
 * <h2>为什么失败不往外抛</h2>
 * {@code resolve} 是在 Jackson 序列化过程中被调用的, 那时响应体已经开始写出,
 * 抛异常只会得到一截非法 JSON, 全局异常处理器兜不住。见 {@link ResolveStrategy#resolve}。
 */
@Slf4j
public class UserResolveStrategy implements ResolveStrategy<UserRes> {

    /** 本地副本的存活时间。越短越不容易读到旧数据, 越长越省远程调用 */
    static final Duration LOCAL_TTL = Duration.ofSeconds(60);

    private static final int LOCAL_MAX_SIZE = 10_000;

    /** 回源失败只 WARN 一次, 避免 service-auth 挂掉时日志被刷爆（与 ResolvingSerializer 同一策略） */
    private static final AtomicBoolean LOOKUP_FAILURE_WARNED = new AtomicBoolean();

    private final UserLookup userLookup;

    /**
     * 用 Optional 包装是因为 Caffeine 不接受 null value, 而"查无此人"必须被缓存住 ——
     * 否则一个不存在的 id 每次都会穿透到 auth。
     */
    private final Cache<Long, Optional<UserRes>> localCache = Caffeine.newBuilder()
            .maximumSize(LOCAL_MAX_SIZE)
            .expireAfterWrite(LOCAL_TTL)
            .recordStats()
            .build();

    public UserResolveStrategy(UserLookup userLookup) {
        this.userLookup = userLookup;
    }

    @Override
    public Class<? extends Annotation> annotation() {
        return User.class;
    }

    @Override
    public Map<Long, UserRes> resolveBatch(Collection<Long> ids) {
        Map<Long, UserRes> resolved = new HashMap<>();
        List<Long> misses = new ArrayList<>();

        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            Optional<UserRes> cached = localCache.getIfPresent(id);
            if (cached == null) {
                misses.add(id);
            } else {
                cached.ifPresent(user -> resolved.put(id, user));
            }
        }

        for (Long id : misses) {
            fetchInto(id, resolved);
        }
        return resolved;
    }

    /**
     * 回源单个用户。
     * <p>
     * 注意: 只有调用<b>成功</b>才写缓存。调用失败（超时/服务不可用）时不写,
     * 否则 auth 恢复之后还会被这 60 秒的"查不到"污染, 表现得像用户真的不存在。
     * <p>
     * 异常在这里被吞掉是有意的: 一个列表响应里某个 id 取不到, 不该连累同一响应里
     * 已经拿到的那些用户。代价是<b>失败和"查无此人"对外表现完全一样</b>（都是 id-only）,
     * 所以这条日志是区分二者的唯一线索 —— 见下面为什么要 WARN。
     */
    private void fetchInto(Long id, Map<Long, UserRes> resolved) {
        try {
            UserRes user = userLookup.findById(id);
            localCache.put(id, Optional.ofNullable(user));
            if (user != null) {
                resolved.put(id, user);
            }
        } catch (Exception failure) {
            // 每条失败都打日志会在 auth 挂掉时把日志刷爆, 但只打 debug 又等于没有 ——
            // 生产环境过滤掉 debug 之后, "@User 全在降级"这件事就完全不可见了。
            // 折中: 整个进程只 WARN 一次, 后续转 debug。要长期观测请改用计数器指标。
            if (LOOKUP_FAILURE_WARNED.compareAndSet(false, true)) {
                log.warn("回源查询用户失败, @User 字段已降级为只输出 id。"
                        + "这通常意味着 service-auth 不可用或超时。后续同类失败不再重复记录。", failure);
            } else {
                log.debug("回源查询用户 {} 失败, 本次按查不到处理（不写缓存）", id, failure);
            }
        }
    }

    /** 供观测/测试使用, 用于确认 L1 是否真的在挡流量 */
    public CacheStats localCacheStats() {
        return localCache.stats();
    }

    public void invalidateLocal(Long id) {
        localCache.invalidate(id);
    }
}
