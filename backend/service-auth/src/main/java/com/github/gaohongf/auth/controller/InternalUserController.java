package com.github.gaohongf.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.auth.client.AuthInternalApi;
import com.github.gaohongf.auth.res.UserAuthorities;
import com.github.gaohongf.auth.res.UserRes;
import com.github.gaohongf.auth.service.UserService;

import lombok.AllArgsConstructor;

/**
 * 服务间调用专用的用户查询端点。
 * <p>
 * 路径常量与 Feign 客户端 {@code com.github.gaohongf.auth.client.UserClient} 共用,
 * 两边写歪的症状是 Feign 侧 404 而本服务完全正常, 很难排查。
 * <p>
 * 标 {@link IsOpen} 是因为调用方（如 service-work-order）是用服务身份发起请求, 没有用户 token,
 * 走鉴权必然 403。代价是这个端点不做任何身份校验, 因此:
 * <ul>
 *   <li>网关必须关掉 discovery locator 并拒绝 /auth/internal/** 从外部进入, 否则等于开放用户枚举</li>
 *   <li>这里只暴露 UserRes 这种可对外公开的字段, 不要把 password 之类的塞进来</li>
 * </ul>
 */
@AllArgsConstructor
@RestController
@RequestMapping(AuthInternalApi.USERS)
public class InternalUserController {

    private final UserService userService;

    /**
     * 按 id 查用户。用户不存在时返回 null, 由响应包装成 {@code data: null} ——
     * 调用方按"查不到"处理, 而不是靠异常。
     */
    @IsOpen
    @GetMapping("/{id}")
    public UserRes findUser(@PathVariable("id") Long id) {
        return userService.findUser(id);
    }

    /**
     * 按 id 查用户的角色与权限, 供各服务的 {@code StpInterface} 使用。
     * <p>
     * 与 {@link #findUser} 不同, 用户不存在时这里返回<b>空授权</b>而不是 null ——
     * 调用方拿到空集合的语义就是"此人无任何权限", 正好是鉴权想要的默认值,
     * 不必区分"用户不存在"和"没有任何授权"（这两种情况下都应该拒绝）。
     */
    @IsOpen
    @GetMapping("/{id}/authorities")
    public UserAuthorities findAuthorities(@PathVariable("id") Long id) {
        return userService.findAuthorities(id);
    }
}
