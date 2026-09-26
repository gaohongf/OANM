package com.github.gaohongf.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * common 模块的缓存自动装配。
 * <p>
 * 走自动装配的原因与 {@link com.github.gaohongf.mybatis.MybatisPlusAutoConfiguration} 相同:
 * 各服务启动类在 com.github.gaohongf.&lt;服务名&gt; 下, 组件扫描扫不到 common 里的类。
 * 之前这里用 @Configuration, 结果就是它在任何服务里都没有生效 ——
 * RedisCacheManager 实际是 Boot 自动配置的默认实现, 而
 * {@link EnhancedRedisCacheManager} 解析 "name#ttl" 的能力从未被用上。
 * <p>
 * before = CacheAutoConfiguration 是为了抢在 Boot 之前注册 cacheManager,
 * 让 Boot 那侧的 @ConditionalOnMissingBean(CacheManager.class) 正常退让。
 */
@AutoConfiguration(before = CacheAutoConfiguration.class)
@EnableCaching
public class CacheConfiguration {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            StringRedisTemplate stringRedisTemplate
    ) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()))
                .disableCachingNullValues();
        return new EnhancedRedisCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory), configuration, stringRedisTemplate);
    }


}
