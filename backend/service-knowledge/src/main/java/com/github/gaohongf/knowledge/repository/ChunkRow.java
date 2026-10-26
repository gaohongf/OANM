package com.github.gaohongf.knowledge.repository;

/**
 * 待写入的一段分块。
 *
 * @param chunkIndex 同一文章内从 0 递增
 * @param content    分块原文（不含前置标题，原文用于搜索结果展示）
 * @param embedding  带标题上下文嵌入的向量
 */
public record ChunkRow(int chunkIndex, String content, float[] embedding) {
}
