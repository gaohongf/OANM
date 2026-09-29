package com.github.gaohongf.auth.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.auth.entity.po.intermediate.UserRoleEntity;

/**
 * 用户-角色关联表 DAO。
 *
 * <h2>该表无独立主键</h2>
 * 主键是 {@code (user_id, role_name)} 这对组合, 所以 BaseMapper 的
 * {@code updateById} / {@code deleteById} 用不了。
 *
 * <h2>为什么删除是物理删除</h2>
 * 与 {@link RolePermissionDao} 同理: 主键是物理组合, 逻辑删除后重新插入同一对会撞主键,
 * 症状是<b>收回某个角色之后就再也授不回来了</b>。墓碑行也没有任何消费者。
 * 详见 {@link RolePermissionDao} 的类注释。
 */
@Mapper
public interface UserRoleDao extends BaseMapper<UserRoleEntity> {

    /**
     * 批量新增用户-角色关联关系。
     * <p>
     * 注意这是自定义 XML, 不走 MyBatis-Plus 的插入路径, 所以
     * {@code AuditMetaObjectHandler} 的自动填充对它无效 —— 调用方必须自己填审计字段。
     *
     * @param list 待新增的关联关系, 不可为空
     * @return 实际影响行数
     */
    int insertBatch(@Param("list") List<UserRoleEntity> list);

    /**
     * 物理删除某用户的全部角色关联。
     * <p>
     * 用于"整体替换"角色时的清空步骤。
     *
     * @param userId 用户 id
     * @return 实际删除行数
     */
    int deleteByUserId(@Param("userId") Long userId);
}
