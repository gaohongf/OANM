package com.github.gaohongf.auth.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.auth.entity.po.PermissionEntity;

/**
 * 权限表 DAO
 */
@Mapper
public interface PermissionDao extends BaseMapper<PermissionEntity> {

    /**
     * 查询用户通过其角色间接拥有的全部权限 (已去重)
     *
     * @param userId 用户ID
     * @return 权限列表, 用户不存在或无权限时返回空集合
     */
    List<PermissionEntity> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户拥有的全部权限标识 (已去重), 供鉴权时做权限匹配
     *
     * @param userId 用户ID
     * @return 权限标识列表, 用户不存在或无权限时返回空集合
     */
    List<String> selectPermissionKeysByUserId(@Param("userId") Long userId);
}
