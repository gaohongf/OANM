package com.github.gaohongf.auth.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.auth.entity.po.intermediate.RolePermissionEntity;

/**
 * 角色-权限关联表 DAO。
 *
 * <h2>该表无独立主键</h2>
 * 主键是 {@code (role_name, permission_id)} 这对组合, 所以 BaseMapper 的
 * {@code updateById} / {@code deleteById} 用不了, 按条件增删请用下面这些方法。
 *
 * <h2>为什么删除是物理删除（这个模块里唯一的例外）</h2>
 * 表的 {@code deleted} 列来自 {@code BaseEntity} 的逻辑删除约定, 但用在这里是错的:
 * 主键是 {@code (role_name, permission_id)} 这对<b>物理</b>组合, 于是
 * "逻辑删除 + 重新插入同一对"会直接撞主键:
 * <pre>
 *   收回权限 → UPDATE ... SET deleted = 1   （行还在）
 *   再次授予 → INSERT 同一对 (role_name, permission_id) → Duplicate entry
 * </pre>
 * 症状是<b>一个权限被收回之后就再也授不回来了</b>, 而且报的是数据库主键冲突,
 * 完全看不出跟"逻辑删除"有关。
 * <p>
 * 更根本的理由是: 这些墓碑行没有任何消费者 —— 没有任何查询会读 {@code deleted = 1}
 * 的授权行。一个没人读、又会弄坏写入的列, 比没有这个列更糟。
 * <p>
 * 对比 {@code users} 表的逻辑删除是有价值的: 软删一个用户保留了那一行, 便于审计,
 * 也避免了外键悬空。而"某角色拥有某权限"这件事本身没有独立身份, 没有留墓碑的意义。
 */
@Mapper
public interface RolePermissionDao extends BaseMapper<RolePermissionEntity> {

    /**
     * 批量新增角色-权限关联关系。
     * <p>
     * 注意这是自定义 XML, <b>不走</b> MyBatis-Plus 的插入路径, 所以
     * {@code AuditMetaObjectHandler} 的自动填充对它无效 —— 调用方必须自己把
     * {@code createBy / createTime / updateBy / updateTime} 填上。
     *
     * @param list 待新增的关联关系, 不可为空
     * @return 实际影响行数
     */
    int insertBatch(@Param("list") List<RolePermissionEntity> list);

    /**
     * 物理删除某角色的全部授权。
     * <p>
     * 用于"整体替换"授权时的清空步骤。必须是物理删除, 理由见类注释。
     *
     * @param roleName 角色名
     * @return 实际删除行数
     */
    int deleteByRoleName(@Param("roleName") String roleName);

    /**
     * 物理删除某权限的全部关联。
     * <p>
     * 用于删除权限时清理关联, 否则会留下指向已删除权限的孤儿行。
     *
     * @param permissionId 权限 id
     * @return 实际删除行数
     */
    int deleteByPermissionId(@Param("permissionId") Long permissionId);
}
