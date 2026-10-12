package com.github.gaohongf.auth.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.github.gaohongf.auth.res.UserAuthorities;
import com.github.gaohongf.auth.res.UserRes;

/**
 * 访问 service-auth 的客户端。
 * <p>
 * 由 {@code com.github.gaohongf.auth.resolve.UserResolveAutoConfiguration} 通过
 * {@code @EnableFeignClients} 注册, 凡引入 common 的服务都能直接用, 无需各自配置。
 * 服务名可通过 {@code oanm.auth.service-name} 覆盖, 默认 {@code service-auth}。
 *
 * <h2>contextId 不能省</h2>
 * 同一个服务有多个 {@code @FeignClient} 时，每个都必须有不同的 contextId，
 * 否则 {@code FeignClientSpecification} 的 bean 名会撞（它默认取服务名），启动直接失败。
 * 这里显式写出来，既是为了和 {@link RegistrationClient} 配对，也让下一个新增客户端的人
 * 一眼看到这条规则。
 *
 * @see com.github.gaohongf.auth.resolve.UserResolveStrategy
 */
@FeignClient(name = "${oanm.auth.service-name:service-auth}", contextId = "userClient",
        path = AuthInternalApi.USERS)
public interface UserClient {

    /**
     * 按 id 查用户。用户不存在时 data 为 null（不是异常）。
     */
    @GetMapping("/{id}")
    ApiResponse<UserRes> findById(@PathVariable("id") Long id);

    /**
     * 按 id 查用户的角色与权限, 供各服务的 {@code StpInterface} 使用。
     * <p>
     * 用户不存在时 data 为 null; 参数见 {@link UserAuthorities} 关于"为什么角色和权限要一起拿"的说明。
     */
    @GetMapping("/{id}/authorities")
    ApiResponse<UserAuthorities> findAuthorities(@PathVariable("id") Long id);

    /**
     * 按角色名查用户 id 列表（已封号/已删除的用户不在其中）。
     * <p>
     * 用于"给某个角色的全部成员发通知"。返回空列表表示这个角色目前没有成员 ——
     * 那是正常情况（不该当失败处理），调用方按"没人要通知"走。
     * <p>
     * 注意这条路是<b>同步</b>的：调用方要么在能阻塞的线程上（servlet 服务本来就允许），
     * 要么自己挪到 {@code boundedElastic}（反应式服务上的做法见鉴权适配器的类注释）。
     */
    @GetMapping(AuthInternalApi.USERS_BY_ROLE)
    ApiResponse<List<Long>> findUserIdsByRole(@PathVariable("roleName") String roleName);
}
