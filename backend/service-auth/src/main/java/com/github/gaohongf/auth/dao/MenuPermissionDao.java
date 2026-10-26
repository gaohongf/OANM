package com.github.gaohongf.auth.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.auth.entity.po.intermediate.MenuPermissionEntity;

/**
 * 菜单-权限关联表 DAO。
 *
 * <h2>该表无独立主键</h2>
 * 主键是 {@code (menu_id, permission_id)} 这对组合, 所以 BaseMapper 的
 * {@code updateById} / {@code deleteById} 用不了, 按条件增删请用下面这些方法。
 *
 * <h2>删除必须是物理删除</h2>
 * 逻辑删除的墓碑行会让"取消某个权限后再挂回来"直接撞联合主键,
 * 而且没有查询会读 {@code deleted = 1} 的行。理由与 {@code RolePermissionDao} 相同。
 */
@Mapper
public interface MenuPermissionDao extends BaseMapper<MenuPermissionEntity> {

    /**
     * 批量新增菜单-权限关联关系。
     * <p>
     * 注意这是自定义 XML, <b>不走</b> MyBatis-Plus 的插入路径, 所以
     * {@code AuditMetaObjectHandler} 的自动填充对它无效 —— 调用方必须自己把
     * {@code createBy / createTime / updateBy / updateTime} 填上。
     *
     * @param list 待新增的关联关系, 不可为空
     * @return 实际影响行数
     */
    int insertBatch(@Param("list") List<MenuPermissionEntity> list);

    /**
     * 物理删除某个菜单的全部关联。
     * <p>
     * 用于"整体替换"门禁时的清空步骤, 以及删除菜单时清理关联。
     *
     * @param menuId 菜单 id
     * @return 实际删除行数
     */
    int deleteByMenuId(@Param("menuId") Long menuId);
}
