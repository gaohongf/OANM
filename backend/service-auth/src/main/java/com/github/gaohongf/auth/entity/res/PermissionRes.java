package com.github.gaohongf.auth.entity.res;

import com.github.gaohongf.auth.entity.po.PermissionEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionRes {

    private Long id;

    /** 权限标识, 如 {@code GET:/api/ops/work_order/{id}} 或 {@code *} */
    private String permissionKey;

    private String label;

    /**
     * 是否是内置的超级管理员权限。
     * <p>
     * 刻意<b>不声明字段</b>、只提供 getter: 它是 {@code permissionKey} 的纯函数,
     * 存成字段就多一处可能和 key 不一致的状态。Jackson 会从这个 getter 序列化出
     * {@code builtIn}, 前端据此禁用改名/删除按钮。MapStruct 也不会试图给它赋值。
     */
    public boolean isBuiltIn() {
        return PermissionEntity.SUPER_KEY.equals(permissionKey);
    }
}
