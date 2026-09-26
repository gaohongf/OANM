package com.github.gaohongf.auth.resolve;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;

import com.github.gaohongf.auth.client.UserClient;

/**
 * 提供"经 Feign 回源 service-auth"的取数口。只有真正需要跨服务取用户的服务才装配。
 *
 * <h2>为什么要卡负载均衡器而不是 {@link FeignClient}</h2>
 * {@code spring-cloud-starter-openfeign} 把 loadbalancer 声明成了 {@code <optional>true</optional>},
 * 不会传递过来; 而 {@link UserClient} 用的是 {@code lb://} 地址, 没有负载均衡器时
 * 第一次调用就炸 {@code IllegalStateException: No Feign Client for loadBalancing defined}。
 * common 会把这个 starter 带给所有服务, 所以 service-auth 里也能看到 {@code FeignClient} 类 ——
 * 只卡它等于没卡, 必须卡负载均衡器自己的类。
 * <p>
 * 这里按<b>类名</b>而不是 {@code Class} 引用: common 自己并不依赖 spring-cloud-loadbalancer,
 * 那个类在编译期不可见。用字符串既表达了"有才生效", 也不用为了写一行条件给 common 加依赖。
 *
 * <h2>{@code @EnableFeignClients} 为什么放这里而不是各服务启动类上</h2>
 * 为了让 {@code @User} 真正做到"引入 common 就能用"。如果让每个服务自己声明, 漏加的服务会
 * <b>静默退化</b>成输出裸 id（注册表里没有策略, Jackson 就按普通 Long 序列化）, 很难发现。
 *
 * <h2>service-auth 不走这条路</h2>
 * 它的数据就在本地, 没有理由为取自己的数据绕一圈 HTTP（更没理由为了能调自己而引入负载均衡器）。
 * 它在自己模块里提供 {@link UserLookup} 的实现, 本类整体退避。前提是那份实现必须是
 * <b>普通用户配置</b>—— 用户配置先于自动配置处理, 这里的 {@code @ConditionalOnMissingBean} 才来得及看到它。
 */
@AutoConfiguration
@ConditionalOnClass(value = FeignClient.class, name = "org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory")
@ConditionalOnMissingBean(UserLookup.class)
@EnableFeignClients(basePackages = "com.github.gaohongf.auth.client")
public class FeignUserLookupAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeignUserLookup feignUserLookup(UserClient userClient) {
        return new FeignUserLookup(userClient);
    }
}
