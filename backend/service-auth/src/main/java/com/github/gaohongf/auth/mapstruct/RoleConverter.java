package com.github.gaohongf.auth.mapstruct;

import java.util.List;

import org.mapstruct.Mapper;

import com.github.gaohongf.auth.entity.po.RoleEntity;
import com.github.gaohongf.auth.entity.res.RoleRes;

/**
 * 角色 → RoleRes。字段同名, 不需要 {@code @Mapping}。
 */
@Mapper(componentModel = "spring")
public interface RoleConverter {

    RoleRes toRes(RoleEntity entity);

    List<RoleRes> toResList(List<RoleEntity> entities);
}
