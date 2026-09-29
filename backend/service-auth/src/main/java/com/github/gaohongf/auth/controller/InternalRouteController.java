package com.github.gaohongf.auth.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.auth.client.AuthInternalApi;
import com.github.gaohongf.auth.gateway.RouteChangeNotifier;
import com.github.gaohongf.auth.res.GatewayRouteRes;
import com.github.gaohongf.auth.service.ApiRouteService;
import com.lingyun.base.rsm.str.RString;

import lombok.AllArgsConstructor;

/**
 * 给 api-gateway 用的内部端点。
 *
 * <h2>为什么标 {@code @IsOpen}</h2>
 * 网关是服务身份发起的请求, 没有用户 token, 走鉴权必然 403。所以这两个端点不做身份校验。
 *
 * <h2>为什么它们不可从外部到达</h2>
 * 路径是 {@code /auth/internal/**}, <b>不以 {@code /api} 开头</b>, 而网关的路由谓词只匹配
 * {@code /api/**}。所以外部请求在网关侧根本匹配不到路由 —— 这是靠路径约定保证的,
 * 不依赖"记得关掉 discovery.locator"这类开关的正确性。新增内部端点时请沿用这个前缀。
 *
 * <h2>为什么有两个端点</h2>
 * {@link #routes()} 是拉数据, {@link #version()} 是拿一个便宜的"变了没有"判断。
 * 网关靠 Redis 广播即时感知变更, 但 pub/sub 是发后即忘的, 掉线期间的消息会丢,
 * 所以它还要定期比对版本号兜底。只为了比对而每次拉全量路由是浪费。
 */
@AllArgsConstructor
@RestController
@RequestMapping(AuthInternalApi.ROUTES)
public class InternalRouteController {

    private final ApiRouteService apiRouteService;
    private final RouteChangeNotifier routeChangeNotifier;

    /**
     * 启用中的全部路由, 形状对齐网关的 {@code RouteDefinition}。
     * <p>
     * 网关拿到之后是全量<b>替换</b>自己内存里的路由集合, 所以这个接口必须是幂等的
     * 全量快照, 不能是增量。
     */
    @IsOpen
    @GetMapping
    public List<GatewayRouteRes> routes() {
        return apiRouteService.listEnabledAsGatewayRoutes();
    }

    /**
     * 启用中的路由路径模式，如 {@code ["/api/ops/**"]}。
     * <p>
     * 各服务启动自检用它判断"我的端点有没有网关路由能到达"。一个端点若不被任何路由覆盖，
     * 从外部访问就是 404，而服务自己一切正常 —— 这种情况不主动报出来，排查方向很容易跑偏。
     */
    @IsOpen
    @GetMapping("/patterns")
    public List<String> patterns() {
        return apiRouteService.listEnabledPathPatterns();
    }

    /**
     * 当前路由版本号（字符串形态的递增整数）。
     * <p>
     * 网关记住自己上次拉取时的版本号, 定期比对; 不一致才重新拉 {@link #routes()}。
     *
     * <h3>必须返回 {@link RString} 而不是裸 {@code String}</h3>
     * lingyun 的 {@code ResponseBodyAdvice} 在转换成 {@code StringHttpMessageConverter} 时
     * 会<b>跳过包装</b>（它的 {@code supports()} 对字符串响应返回 false）。
     * 所以直接 {@code return "5";} 会把裸的 {@code 5} 发给客户端, 而其他内部端点发的都是
     * {@code {code,data,msg,type}} 信封 —— 同一个 Controller 里两种形状,
     * 调用方按信封解析时就会一直失败。
     * <p>
     * 本项目踩过这个坑: 网关的版本轮询因此每轮都解析失败, 于是<b>兜底机制静默失效</b>,
     * 而它看起来还在正常工作（定时器照样在跑）。
     * <p>
     * {@link RString} 会被解开成里面的字符串, 再由 RSM 包装成 {@code data} ——
     * 这正是 {@code standard.md} 里"响应是字符串时请使用 RString"那条规矩的由来。
     */
    @IsOpen
    @GetMapping("/version")
    public RString version() {
        return RString.warp(routeChangeNotifier.currentVersion());
    }
}
