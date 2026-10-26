package com.github.gaohongf.knowledge.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.knowledge.entity.po.KnowledgeArticleRevisionEntity;

/** 知识库文章修订 DAO */
@Mapper
public interface KnowledgeArticleRevisionDao extends BaseMapper<KnowledgeArticleRevisionEntity> {
}
