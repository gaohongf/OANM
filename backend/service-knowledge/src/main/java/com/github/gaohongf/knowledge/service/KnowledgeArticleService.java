package com.github.gaohongf.knowledge.service;

import java.util.List;

import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.knowledge.entity.req.SaveArticleCommand;
import com.github.gaohongf.knowledge.entity.res.KnowledgeArticleDetailRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeArticleRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeManageArticleRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeManageDetailRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeRevisionRes;

/** 知识库文章的读写、发布与修订 */
public interface KnowledgeArticleService {

    /** 公开列表：只返回已发布文章 */
    PageRes<KnowledgeArticleRes> pagePublic(long current, long size, Long categoryId, String keyword);

    /** 公开详情：只返回已发布文章，并原子递增浏览次数 */
    KnowledgeArticleDetailRes findPublicDetail(Long id);

    /** 管理列表：全部状态，带索引状态 */
    PageRes<KnowledgeManageArticleRes> pageManage(long current, long size, Long categoryId,
                                                 String status, String keyword);

    KnowledgeManageDetailRes findManageDetail(Long id);

    Long create(SaveArticleCommand command);

    void update(Long id, SaveArticleCommand command);

    void changeStatus(Long id, String status);

    void delete(Long id);

    void reindex(Long id);

    List<KnowledgeRevisionRes> listRevisions(Long articleId);

    KnowledgeRevisionRes findRevision(Long articleId, Long revisionId);

    /** 回滚正文、摘要、标题与分类；不改变发布状态 */
    void restore(Long articleId, Long revisionId);
}
