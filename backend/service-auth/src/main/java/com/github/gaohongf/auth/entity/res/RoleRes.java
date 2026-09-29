package com.github.gaohongf.auth.entity.res;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色。
 * <p>
 * {@code roleName} 是 {@code roles} 表的主键, 创建后不可修改（见
 * {@code AuthRsm.ROLE_NAME_IMMUTABLE}）, 所以它既是标识也是名称。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleRes {

    private String roleName;

    /** 显示用的标签, 如 管理员 */
    private String label;
}
