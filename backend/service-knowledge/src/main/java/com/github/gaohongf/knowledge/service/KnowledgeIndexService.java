package com.github.gaohongf.knowledge.service;

/** 文章分块与嵌入的异步重建 */
public interface KnowledgeIndexService {

    /**
     * 异步重建一篇文章的索引。
     *
     * <p>
     * 调用即返回；失败原因写进文章的 {@code index_error}，不向调用方抛。
     */
    void rebuildAsync(Long articleId);

    /** 索引线程池拒绝时把文章标成 FAILED，避免它永远停在 INDEXING */
    void markQueueRejected(Long articleId, String reason);
}
