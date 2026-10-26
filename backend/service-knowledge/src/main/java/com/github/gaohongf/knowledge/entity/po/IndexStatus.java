package com.github.gaohongf.knowledge.entity.po;

/**
 * 文章在向量索引里的状态（派生状态）。
 *
 * <p>
 * 状态流转：
 * <pre>
 * NONE ──发布/重建──▶ INDEXING ──成功──▶ READY
 *                        └────失败──▶ FAILED ──重建──▶ INDEXING
 * 取消发布/删除 ──▶ NONE
 * </pre>
 * {@code FAILED} 时旧的 chunks 仍然保留，语义检索继续用上一次成功索引的内容 ——
 * 整篇文章从搜索结果里消失比"内容稍旧"更糟。
 */
public enum IndexStatus {

    /** 没有索引（草稿、已取消发布，或从未成功索引过） */
    NONE,

    /** 正在异步重建 */
    INDEXING,

    /** 索引与当前正文一致 */
    READY,

    /** 最近一次重建失败，index_error 里有原因 */
    FAILED
}
