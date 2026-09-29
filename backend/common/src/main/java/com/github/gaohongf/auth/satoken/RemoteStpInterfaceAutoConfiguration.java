package com.github.gaohongf.auth.satoken;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;

import com.github.gaohongf.auth.client.UserClient;
import com.github.gaohongf.auth.resolve.FeignUserLookupAutoConfiguration;

import cn.dev33.satoken.stp.StpInterface;

/**
 * 给"自己鉴权"的服务装上从 service-auth 取权限的 {@link StpInterface}。
 *
 * <h2>为什么要卡负载均衡器</h2>
 * 与 {@link FeignUserLookupAutoConfiguration} 同理: {@code UserClient} 用的是 {@code lb://} 地址,
 * {@code spring-cloud-starter-openfeign} 把 loadbalancer 声明成 {@code <optional>true</optional>}
 * 不会传递过来, 少了它在第一次调用时才炸。common 会把 openfeign 带给所有服务,
 * 所以卡 {@link FeignClient} 等于没卡, 必须卡负载均衡器自己的类（按类名引用, 因为
 * common 自己并不依赖 spring-cloud-loadbalancer）。
 *
 * <h2>service-auth 会退避</h2>
 * 它有自己的 {@code StpInterfaceImpl} 直接查本地库, 是普通 {@code @Component}。
 * 用户配置先于自动配置处理, 所以这里的 {@code @ConditionalOnMissingBean} 看得到它,
 * 整个自动配置退避 —— service-auth 不会为了查自己的数据走一次网络。
 *
 * <h2>顺序</h2>
 * 必须晚于 {@link FeignUserLookupAutoConfiguration} —— {@code UserClient} 这个 bean 是它通过
 * {@code @EnableFeignClients} 注册的, 抢在前面会因为找不到 {@code UserClient} 而启动失败。
 */
@AutoConfiguration(after = FeignUserLookupAutoConfiguration.class)
@ConditionalOnClass(value = FeignClient.class, name = "org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory")
@ConditionalOnMissingBean(StpInterface.class)
public class RemoteStpInterfaceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RemoteStpInterface remoteStpInterface(UserClient userClient) {
        return new RemoteStpInterface(userClient);
    }
}
