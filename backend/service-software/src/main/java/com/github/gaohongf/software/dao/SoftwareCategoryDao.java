package com.github.gaohongf.software.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.software.entity.po.SoftwareCategoryEntity;

/** 分类 DAO。查询都用条件构造器表达，不需要 XML */
@Mapper
public interface SoftwareCategoryDao extends BaseMapper<SoftwareCategoryEntity> {
}
