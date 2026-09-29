package com.github.gaohongf.auth.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.auth.entity.po.ApiRouteEntity;

import org.apache.ibatis.annotations.Mapper;

/**
 * 网关路由表 DAO。
 * <p>
 * 查询都在 BaseMapper 的能力范围内(按 id / 按 path_pattern / 列全部启用项),
 * 不需要自定义 XML。注意逻辑删除条件由 {@code @TableLogic} 自动拼装。
 */
@Mapper
public interface ApiRouteDao extends BaseMapper<ApiRouteEntity> {
}
