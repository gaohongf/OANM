package com.github.gaohongf.auth.client;

/**
 * service-auth 内部端点路径。
 * <p>
 * 客户端（{@link UserClient}）与服务端 Controller 共用这里的常量, 避免两边路径写歪 ——
 * 写歪的症状是 Feign 侧的 404, 而服务本身完全正常, 排查起来很费时间。
 * <p>
 * 这些端点走 {@code @IsOpen} 不做鉴权, <b>不允许</b>从网关暴露出去。
 */
public final class AuthInternalApi {

    /** 按 id 查单个用户, 响应 data 为 UserRes */
    public static final String USER_BY_ID = "/auth/internal/users/{id}";

    /** 上面路径的控制器前缀 */
    public static final String USERS = "/auth/internal/users";

    private AuthInternalApi() {
    }
}
