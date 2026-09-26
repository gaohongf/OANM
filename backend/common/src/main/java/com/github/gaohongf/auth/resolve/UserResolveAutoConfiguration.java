package com.github.gaohongf.auth.resolve;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 把 {@code @User} 接上一条已存在的取数口。
 * <p>
 * 这一层刻意不认识 Feign: 它只要能拿到一个 {@link UserLookup} bean, 剩下的缓存和降级
 * 由 {@link UserResolveStrategy} 负责。取数口从哪来是另一件事 ——
 * 普通服务由 {@link FeignUserLookupAutoConfiguration} 提供, service-auth 由它自己提供。
 *
 * <h2>为什么用 {@code @ConditionalOnBean} 而不是 {@code @ConditionalOnMissingBean}</h2>
 * 没有取数口时创建策略只会得到一个启动期 UnsatisfiedDependency, 不如干脆不装配。
 * {@code @ConditionalOnBean} 在自动配置里只看<b>已经处理过</b>的配置所定义的 bean, 所以
 * 这里必须显式声明 {@code after} 保证顺序; service-auth 那份是用户配置, 天然先于所有自动配置。
 */
@AutoConfiguration(after = FeignUserLookupAutoConfiguration.class)
@ConditionalOnBean(UserLookup.class)
public class UserResolveAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UserResolveStrategy userResolveStrategy(UserLookup userLookup) {
        return new UserResolveStrategy(userLookup);
    }
}
