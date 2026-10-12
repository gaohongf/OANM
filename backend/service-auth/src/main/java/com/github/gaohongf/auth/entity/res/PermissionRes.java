package com.github.gaohongf.auth.entity.res;

import com.github.gaohongf.auth.entity.po.PermissionEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限。
 *
 * <h2>id 为什么是 String 而不是 Long</h2>
 * 理由与 {@code WorkOrderRes} 上那段相同: 主键是雪花 id(19 位), JS 的 {@code Number}
 * 到 {@code 2^53 - 1} 就到底了, 序列化成 JSON 数字后前端拿到的是<b>已经变了的值</b>。
 * 这里比工单那边更要紧 —— 前端拿这个 id 去拼 {@code PUT /permissions/{id}} 和删除,
 * 而库里同一批自注册权限的 id 只差个位数(如 ...314 与 ...321), 取整后会<b>塌成同一个值</b>,
 * 于是"改 A 却打到了 B"。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionRes {

    private String id;

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
