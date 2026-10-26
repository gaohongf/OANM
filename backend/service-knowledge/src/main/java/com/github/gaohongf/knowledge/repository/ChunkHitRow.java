package com.github.gaohongf.knowledge.repository;

/**
 * 一次向量检索命中的分块。
 *
 * @param score 余弦相似度（1 - 余弦距离），越大越相似
 */
public record ChunkHitRow(long articleId, int chunkIndex, String content, double score) {
}
