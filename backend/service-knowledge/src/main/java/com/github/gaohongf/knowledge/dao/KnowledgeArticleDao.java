package com.github.gaohongf.knowledge.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.knowledge.entity.po.KnowledgeArticleEntity;

/** 知识库文章 DAO */
@Mapper
public interface KnowledgeArticleDao extends BaseMapper<KnowledgeArticleEntity> {
}
