package com.github.gaohongf.auth.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.github.gaohongf.auth.mq.model.RegisterServiceAuth;

/**
 * 自注册与启动自检要用的内部接口。
 * <p>
 * 与 {@link UserClient} 分开是因为用途不同: 那个是"解析用户字段"用的（业务路径，
 * 每个请求都可能走），这个是启动期一次性用的。放在一起会让两件事的缓存策略、
 * 超时要求混在一起 —— 这个可以慢慢等，那个必须快速失败。
 *
 * <h2>contextId 不能省</h2>
 * 同一个服务有多个 {@code @FeignClient} 时，每个都必须有<b>不同的 contextId</b>：
 * 不加的话 Feign 会用"服务名"作为 {@code FeignClientSpecification} 的 bean 名，
 * 两个客户端撞名，启动时直接失败
 * （{@code The bean 'xxx.FeignClientSpecification' could not be registered}）。
 * 注意这个报错发生在启动期，是硬失败而不是等到调用时才出问题。
 */
@FeignClient(name = "${oanm.auth.service-name:service-auth}", contextId = "registrationClient")
public interface RegistrationClient {

    /**
     * 上报本服务需要权限的端点。幂等：同一个键重复上报不会产生重复行，也不会改动它们的标签。
     *
     * @return data 为本次<b>新建</b>的键（已存在的不计入）
     */
    @PostMapping(AuthInternalApi.REGISTER)
    ApiResponse<List<String>> register(@RequestBody RegisterServiceAuth registration);

    /** 启用中的网关路由路径模式，供启动自检比对 */
    @GetMapping(AuthInternalApi.ROUTE_PATTERNS)
    ApiResponse<List<String>> routePatterns();
}
