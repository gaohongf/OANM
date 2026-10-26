package com.github.gaohongf.knowledge.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.knowledge.entity.po.KnowledgeCategoryEntity;

/** 知识库分类 DAO */
@Mapper
public interface KnowledgeCategoryDao extends BaseMapper<KnowledgeCategoryEntity> {
}
