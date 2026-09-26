package com.github.gaohongf.auth.cache;

public final class CacheKeyConstants {
    public static final String KEY_TTL_SEPARATOR = "#";
    /**
     *  对于用户数据的缓存键
     */
    public static final String USER = "user";
    public static final String USER_TTL = "user" + KEY_TTL_SEPARATOR;

    /**
     * 用户缓存的存活时长（秒）。
     * <p>
     * 放在这里而不是 {@code UserServiceImpl} 里, 是为了让它和上面的 {@link #USER_TTL} 挨着 ——
     * 这两件事必须一起改。{@code EnhancedRedisCacheManager.createRedisCache} 把 cacheName
     * 末尾 {@code #} 之后的部分解析成 TTL, 同时把 {@code #} 之前的部分当作真正的 cacheName。
     * 也就是说 {@code @CacheConfig(cacheNames = USER_TTL + USER_CACHE_TTL_SECONDS)}
     * 最终得到的是"名叫 user、TTL 12 小时"的缓存。
     * <p>
     * 副作用: 改这个值等于同时改了 Redis 里的 key 前缀, 旧的 {@code user#43200::*} 不会自动清理。
     */
    public static final long USER_CACHE_TTL_SECONDS = 12 * 60 * 60L;
}