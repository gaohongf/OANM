package com.github.gaohongf.auth.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.auth.entity.po.MenuEntity;

import org.apache.ibatis.annotations.Mapper;

/**
 * 菜单表 DAO。
 * <p>
 * 菜单树只有几十到几百个节点, 建树在内存里做(一次查询取全部节点, 再按 parent_id 组装),
 * 不需要递归 SQL, 所以这里没有任何自定义查询。
 */
@Mapper
public interface MenuDao extends BaseMapper<MenuEntity> {
}
