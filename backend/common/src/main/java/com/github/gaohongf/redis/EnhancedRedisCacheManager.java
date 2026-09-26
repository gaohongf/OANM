package com.github.gaohongf.redis;

import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;

import java.time.Duration;

public class EnhancedRedisCacheManager extends RedisCacheManager {
    private final StringRedisTemplate redisTemplate;

    public EnhancedRedisCacheManager(
            RedisCacheWriter cacheWriter,
            RedisCacheConfiguration defaultCacheConfiguration,
            StringRedisTemplate redisTemplate
    ) {
        super(cacheWriter, defaultCacheConfiguration);
        this.redisTemplate = redisTemplate;
    }

    @Override
    @NonNull
    protected RedisCache createRedisCache(
            @NonNull String name,
            RedisCacheConfiguration cacheConfiguration
    ) {
        if (cacheConfiguration == null) {
            cacheConfiguration = getDefaultCacheConfiguration();
        }
        String secondstring = name.substring(name.lastIndexOf("#") + 1);
        try {
            long ttl = Long.parseLong(secondstring);
            return new EnhancedRedisCache(RedisUtils.getTrueName(name), getCacheWriter(), cacheConfiguration.entryTtl(Duration.ofSeconds(ttl)));
        } catch (NumberFormatException e) {
            return new EnhancedRedisCache(name, getCacheWriter(), cacheConfiguration);
        }
    }

    class EnhancedRedisCache extends RedisCache {

        protected EnhancedRedisCache(
                String name,
                RedisCacheWriter cacheWriter,
                RedisCacheConfiguration cacheConfiguration
        ) {
            super(name, cacheWriter, cacheConfiguration);

        }

        /**
         * 删掉单个 key。
         * <p>
         * 原实现走的是 <code>redisTemplate.keys(getName() + "::" + k)</code>:
         * {@code KEYS} 是 O(N) 全库扫描且阻塞整个 Redis, 而这里的 pattern 里根本
         * <b>没有通配符</b> —— 它只是想拼出一个确定的 key 再删掉。用 {@code DELETE} 是 O(1)。
         * <p>
         * 注意只有 String 类型的 key 走这条路: 缓存 key 由
         * {@code RedisSerializer.string()} 序列化, 所以 String 是正常情况,
         * 别的类型落回 {@code super.evict} 交给 RedisCache 自己处理。
         */
        @Override
        public void evict(@NonNull Object key) {
            if (key instanceof String k) {
                redisTemplate.delete(getName() + "::" + k);
                return;
            }
            super.evict(key);
        }

        @Override
        public void clear() {
            super.clear();
        }
    }


}
