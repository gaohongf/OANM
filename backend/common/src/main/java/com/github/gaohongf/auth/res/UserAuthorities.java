package com.github.gaohongf.auth.res;

import java.util.List;

/**
 * 一个用户的全部授权信息：角色名与权限键。
 *
 * <h2>为什么角色和权限要一起返回</h2>
 * sa-token 的 {@code StpInterface} 有 {@code getRoleList} 和 {@code getPermissionList} 两个方法,
 * 各服务的 {@code RemoteStpInterface} 两个都要实现。
 * 如果只提供权限端点、把 {@code getRoleList} 写成返回空集合, 那么将来任何人在普通服务里
 * 写一句 {@code StpUtil.hasRole("admin")} 都会<b>静默地永远返回 false</b> —— 不报错、
 * 不告警, 只是权限判断失效。合并成一次调用既省一次往返, 也堵掉这个坑。
 *
 * @param roles       角色名, 如 {@code ["admin"]}
 * @param permissions 权限键, 如 {@code ["GET:/api/ops/work_order/{id}"]}
 */
public record UserAuthorities(List<String> roles, List<String> permissions) {

    /** 空授权, 用于"用户不存在"和"取不到"两种情况下的兜底 */
    public static final UserAuthorities EMPTY = new UserAuthorities(List.of(), List.of());

    public UserAuthorities {
        // 反序列化时可能给 null, 统一成空集合, 免得每个使用点都判一次
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
