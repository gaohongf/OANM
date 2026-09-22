package com.github.gaohongf.auth.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.auth.entity.po.PermissionEntity;
import com.github.gaohongf.auth.entity.po.RoleEntity;

/**
 * 角色表 DAO
 */
@Mapper
public interface RoleDao extends BaseMapper<RoleEntity> {

    /**
     * 查询角色已授予的权限列表
     *
     * @param roleName 角色名
     * @return 权限列表, 角色不存在或无权限时返回空集合
     */
    List<PermissionEntity> selectPermissionsByRoleName(@Param("roleName") String roleName);
}
