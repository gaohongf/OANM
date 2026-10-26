package com.github.gaohongf.auth.entity.po.intermediate;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单-权限关联表。
 *
 * <h2>该表无独立主键</h2>
 * 主键是 {@code (menu_id, permission_id)} 这对组合, 所以 BaseMapper 的
 * {@code updateById} / {@code deleteById} 用不了, 按条件增删请用 {@code MenuPermissionDao} 上的方法。
 *
 * <h2>为什么删除是物理删除</h2>
 * 与 {@code role_permissions} 同理: 主键是物理组合, 逻辑删除留下的墓碑行会让
 * "取消某个权限后再挂回来"直接撞主键。这张表也没有任何查询会读 {@code deleted = 1} 的行。
 *
 * <h2>语义</h2>
 * 一个菜单可以有多条关联, 持有其中任意一个权限即可见; 一条都没有表示登录即可见。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("menu_permissions")
public class MenuPermissionEntity extends BaseEntity {
    private Long menuId;
    private Long permissionId;
}
