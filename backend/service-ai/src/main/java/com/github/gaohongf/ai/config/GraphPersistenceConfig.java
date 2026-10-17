package com.github.gaohongf.ai.config;

import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;

@Configuration
public class GraphPersistenceConfig {

    @Bean
    public RedisSaver redisSaver(RedissonClient redissonClient) {
        // 直接使用构造器创建 RedisSaver
        return RedisSaver.builder().redisson(redissonClient).build();
    }
}