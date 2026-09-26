package com.github.gaohongf.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.github.gaohongf.auth.res.UserRes;

/**
 * 访问 service-auth 的客户端。
 * <p>
 * 由 {@code com.github.gaohongf.auth.resolve.UserResolveAutoConfiguration} 通过
 * {@code @EnableFeignClients} 注册, 凡引入 common 的服务都能直接用, 无需各自配置。
 * 服务名可通过 {@code oanm.auth.service-name} 覆盖, 默认 {@code service-auth}。
 *
 * @see com.github.gaohongf.auth.resolve.UserResolveStrategy
 */
@FeignClient(name = "${oanm.auth.service-name:service-auth}", path = AuthInternalApi.USERS)
public interface UserClient {

    /**
     * 按 id 查用户。用户不存在时 data 为 null（不是异常）。
     */
    @GetMapping("/{id}")
    ApiResponse<UserRes> findById(@PathVariable("id") Long id);
}
