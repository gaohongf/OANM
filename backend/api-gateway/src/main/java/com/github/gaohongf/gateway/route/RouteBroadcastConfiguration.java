package com.github.gaohongf.gateway.route;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import lombok.extern.slf4j.Slf4j;

/**
 * 订阅 service-auth 的路由变更广播, 收到就立刻重拉路由。
 *
 * <h2>为什么用它而不是轮询</h2>
 * 只靠轮询的话, 改一条路由最坏要等一个轮询周期才生效; 只靠广播则会在丢消息后长期不一致
 * （pub/sub 是发后即忘的）。两者都要: 广播负责"快", 轮询负责"不漏"。
 *
 * <h2>频道名是复制过来的</h2>
 * {@code common} 里有 {@code RedisChannels.ROUTES_CHANGED}, 但网关不能依赖 common
 * （它是 Servlet 技术栈, 和网关的 WebFlux 冲突, 见 {@code AuthRouteClient} 的说明）。
 * 所以这里手写一份, 改动时两边都要改。
 */
@Slf4j
@Configuration
public class RouteBroadcastConfiguration {

    /** 与 common 的 {@code RedisChannels.ROUTES_CHANGED} 保持一致 */
    private static final String ROUTES_CHANGED_CHANNEL = "oanm:routes:changed";

    @Bean
    public RedisMessageListenerContainer routeBroadcastListenerContainer(
            RedisConnectionFactory connectionFactory,
            DynamicRouteRefresher refresher) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> {
                    // 载荷是版本号, 但这里<b>不</b>拿它做判断: 广播的语义就是"变了", 直接重拉。
                    // 版本号是留给轮询兜底比对用的（见 RouteVersionPoller）—— 广播路径上再做一次
                    // 比对只会多一次分支, 却省不掉那次拉取。
                    String version = new String(message.getBody(), StandardCharsets.UTF_8);
                    log.debug("收到路由变更广播, version={}", version);
                    refresher.refresh("广播");
                },
                new ChannelTopic(ROUTES_CHANGED_CHANNEL));
        return container;
    }
}
