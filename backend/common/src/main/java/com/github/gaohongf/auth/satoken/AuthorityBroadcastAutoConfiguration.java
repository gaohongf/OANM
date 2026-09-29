package com.github.gaohongf.auth.satoken;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.github.gaohongf.redis.RedisChannels;

import lombok.extern.slf4j.Slf4j;

/**
 * 订阅授权变更广播, 让<b>其他服务</b>的授权缓存也能立刻失效。
 *
 * <h2>不做这件事会怎样</h2>
 * 每个服务各自持有 30 秒 TTL 的本地授权缓存。管理员改了权限之后, 只有 service-auth
 * 本地的缓存被清掉, 其他服务要等自己的 TTL 过期才生效 —— 表现为"改了没生效,
 * 过一会儿又自己好了", 是最容易被当成玄学的那类问题。
 *
 * <h2>为什么只需要"信号"就够</h2>
 * 广播的载荷只有"全量"或"某个 userId", 收到之后做的事情就是清本地缓存, 不需要
 * 变更后的数据本身。所以 pub/sub 消息丢失的后果只是"晚 30 秒", 不会不一致 ——
 * 这也是敢用一个发后即忘的通道的原因。
 *
 * <h2>注册时机</h2>
 * 用 {@code ObjectProvider} 拿 {@link LocalAuthorityCache}: 这个 bean 在 service-auth 里是
 * {@code StpInterfaceImpl}、在普通服务里是 {@code RemoteStpInterface}, 两种情况下都存在,
 * 但都不是本模块定义的。拿不到就静默不订阅（例如某个服务把鉴权整个关掉了）。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(RedisMessageListenerContainer.class)
public class AuthorityBroadcastAutoConfiguration {

    /** 载荷里表示"影响面不确定, 全量失效"的标记 */
    private static final String ALL = "*";

    @Bean
    public RedisMessageListenerContainer authorityBroadcastListenerContainer(
            RedisConnectionFactory connectionFactory,
            ObjectProvider<LocalAuthorityCache> localCacheProvider) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> onAuthorityChanged(
                        new String(message.getBody(), StandardCharsets.UTF_8), localCacheProvider),
                new ChannelTopic(RedisChannels.AUTHORITIES_CHANGED));
        return container;
    }

    private static void onAuthorityChanged(String payload, ObjectProvider<LocalAuthorityCache> localCacheProvider) {
        LocalAuthorityCache cache = localCacheProvider.getIfAvailable();
        if (cache == null) {
            // 没有本地授权缓存可失效 —— 例如整个鉴权被 oanm.auth.enabled=false 关掉了
            return;
        }

        String value = payload == null ? "" : payload.trim();
        if (ALL.equals(value)) {
            cache.invalidateAll();
            log.debug("收到授权全量失效广播, 已清空本地授权缓存");
            return;
        }
        try {
            cache.invalidateUser(Long.valueOf(value));
        } catch (NumberFormatException ignored) {
            // 载荷不认识。宁可全量失效也不要什么都不做 —— 前者只是多几次重查,
            // 后者会让这个进程带着旧授权继续跑
            log.warn("授权变更广播的载荷无法识别: {}, 已改为全量失效", value);
            cache.invalidateAll();
        }
    }
}
