package com.github.gaohongf.knowledge.service;

import java.util.List;

import com.github.gaohongf.knowledge.entity.req.SaveCategoryCommand;
import com.github.gaohongf.knowledge.entity.res.KnowledgeCategoryRes;

/** 知识库分类 */
public interface KnowledgeCategoryService {

    /** 按排序值列出分类，带已发布文章数 */
    List<KnowledgeCategoryRes> list();

    Long create(SaveCategoryCommand command);

    void update(Long id, SaveCategoryCommand command);

    void delete(Long id);
}
