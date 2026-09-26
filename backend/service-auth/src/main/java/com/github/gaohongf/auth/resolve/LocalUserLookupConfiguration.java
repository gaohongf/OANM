package com.github.gaohongf.auth.resolve;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.gaohongf.auth.res.UserRes;
import com.github.gaohongf.auth.service.UserService;

/**
 * service-auth 自己的取数口：直接查本地, 不走 HTTP。
 *
 * <h2>为什么需要它</h2>
 * common 里的 {@code UserResolveAutoConfiguration} 会给每个服务装配一条
 * "Feign 打回 service-auth"的链路。对 service-auth 来说那是自己调自己 ——
 * 数据就在本进程, 绕一圈 HTTP 除了多一次序列化、多一次网络往返、多一个故障点之外
 * 没有任何好处, 还被迫引入一个负载均衡器（否则第一次解析就报
 * {@code No Feign Client for loadBalancing defined}）。
 * <p>
 * 这里提供一个普通的 {@link UserLookup} bean, common 那边的
 * {@code @ConditionalOnMissingBean(UserLookup.class)} 会让整条 Feign 链路退避。
 *
 * <h2>必须是普通 @Configuration, 不能写成 @AutoConfiguration</h2>
 * Spring 先处理用户配置再处理自动配置。这个 bean 必须先于 common 的条件判断可见,
 * 否则条件会得出"没有用户实现"的结论, 又绕回自我 HTTP 调用。
 */
@Configuration(proxyBeanMethods = false)
public class LocalUserLookupConfiguration {

    @Bean
    public UserLookup localUserLookup(UserService userService) {
        return new UserLookup() {
            @Override
            public UserRes findById(Long id) {
                return userService.findUser(id);
            }
        };
    }
}
