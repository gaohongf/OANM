package com.github.gaohongf.knowledge.service;

import com.github.gaohongf.knowledge.entity.req.RetrieveCommand;
import com.github.gaohongf.knowledge.entity.res.KnowledgeRetrieveRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeSearchRes;

/** 语义检索 */
public interface KnowledgeSearchService {

    /** 页面搜索：语义优先，关键字补足；嵌入失败时整体降级为关键字 */
    KnowledgeSearchRes search(String query, Integer limit, Long categoryId);

    /** 内部检索：返回最相关的片段；嵌入失败抛 503 */
    KnowledgeRetrieveRes retrieve(RetrieveCommand command);
}
