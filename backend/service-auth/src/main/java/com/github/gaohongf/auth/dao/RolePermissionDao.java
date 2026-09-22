package com.github.gaohongf.auth.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.auth.entity.po.intermediate.RolePermissionEntity;

/**
 * 角色-权限关联表 DAO
 * <p>
 * 注意: 该表无独立主键 (联合主键 role_name + permission_id), 所以 BaseMapper 的 updateById / deleteById
 * 无法使用, 按主键的增删改请自行使用 QueryWrapper 条件, 或使用下面的批量新增。
 */
@Mapper
public interface RolePermissionDao extends BaseMapper<RolePermissionEntity> {

    /**
     * 批量新增角色-权限关联关系
     *
     * @param list 待新增的关联关系, 不可为空
     * @return 实际影响行数
     */
    int insertBatch(@Param("list") List<RolePermissionEntity> list);
}
