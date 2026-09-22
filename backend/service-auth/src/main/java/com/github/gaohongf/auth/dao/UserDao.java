package com.github.gaohongf.auth.dao;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.auth.entity.po.UserEntity;

/**
 * 用户表 DAO
 */
@Mapper
public interface UserDao extends BaseMapper<UserEntity> {

    /**
     * 查询用户拥有的角色名列表
     *
     * @param userId 用户ID
     * @return 角色名列表, 用户不存在或无角色时返回空集合
     */
    List<String> selectRoleNamesByUserId(@Param("userId") Long userId);
    Optional<UserEntity> selectByUsername(@Param("username") String username);
}
