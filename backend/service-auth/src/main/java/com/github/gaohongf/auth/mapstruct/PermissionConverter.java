package com.github.gaohongf.auth.mapstruct;

import java.util.List;

import org.mapstruct.Mapper;

import com.github.gaohongf.auth.entity.po.PermissionEntity;
import com.github.gaohongf.auth.entity.res.PermissionRes;

/**
 * 权限 → PermissionRes。
 * <p>
 * {@code PermissionRes.builtIn} 是个只有 getter 的计算属性（{@code permissionKey} 的纯函数）,
 * 没有 setter, 所以 MapStruct 不会试图给它赋值 —— 这是刻意的: 存成字段就多一处
 * 可能和 key 不一致的状态。
 */
@Mapper(componentModel = "spring")
public interface PermissionConverter {

    PermissionRes toRes(PermissionEntity entity);

    List<PermissionRes> toResList(List<PermissionEntity> entities);
}
