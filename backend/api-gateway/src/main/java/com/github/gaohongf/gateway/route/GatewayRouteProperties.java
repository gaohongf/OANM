package com.github.gaohongf.gateway.route;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.convert.DurationUnit;

import lombok.Data;

/**
 * 动态路由的配置。
 *
 * <h2>这里刻意<b>不</b>标 @ConfigurationProperties</h2>
 * 前缀声明在 {@code DynamicRouteConfiguration#gatewayRouteProperties()} 的 {@code @Bean}
 * 方法上。同一个前缀声明两遍（类上一次、方法一次）会被
 * {@code spring-boot-configuration-processor} 判定为重复并<b>直接编译失败</b>：
 * <pre>
 *   Duplicate @ConfigurationProperties definition for prefix 'oanm.gateway.routes'
 * </pre>
 * 保留方法上那一份是因为 {@code RouteVersionPoller} 的 {@code @Scheduled} 要用 SpEL
 * 引用这个 bean（{@code #{@gatewayRouteProperties...}}），而只有显式 {@code @Bean}
 * 才能让 bean 名等于方法名。注解放在方法上同样能正常绑定，不缺什么。
 */
@Data
public class GatewayRouteProperties {

    /** service-auth 的服务名, 通过 Nacos 解析 */
    private String serviceName = "service-auth";

    /**
     * 兜底轮询间隔。
     * <p>
     * Redis pub/sub 是发后即忘的, 订阅者掉线期间的消息会永久丢失, 所以还要定期拿版本号
     * 比对一次。这个值就是"最坏情况下, 一次路由变更要多久才能被这个网关实例感知到"的上界 ——
     * 正常情况下广播是毫秒级, 这个值只在丢消息时起作用。
     *
     * <h3>@DurationUnit 不能省</h3>
     * Spring 把<b>裸数字</b>绑定到 {@code Duration} 时, 默认单位是<b>毫秒</b>, 不是秒。
     * 也就是说配置里写 {@code version-poll-interval: 60} 会得到 60<b>毫秒</b> ——
     * 网关会以每 60ms 一次的频率去问 service-auth（实际踩过: 日志被刷屏, 且相当于对
     * service-auth 发起自杀式轮询）。
     * <p>
     * 加上这个注解之后, 裸数字与带后缀的 {@code 60s} 都表示 60 秒, 两种写法都不会错。
     * yml 里也刻意带了 {@code s} 后缀, 让读配置的人一眼看出单位。
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration versionPollInterval = Duration.ofSeconds(60);

    /**
     * last-known-good 缓存文件。
     * <p>
     * 每次成功拉取都会覆盖写一次。service-auth 不可用时网关靠它启动, 避免整个网关没有路由
     * （那会让所有请求 404, 比用稍旧的路由严重得多）。
     */
    private String cacheFile = "./gateway-routes.json";
}
