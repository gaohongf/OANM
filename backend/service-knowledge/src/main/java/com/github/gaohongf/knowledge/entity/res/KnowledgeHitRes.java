package com.github.gaohongf.knowledge.entity.res;

/**
 * 内部检索接口命中的一个片段。
 *
 * <p>
 * 返回片段而不是整篇文章：未来给大模型做 RAG 时只需要最相关的几段，
 * 把整篇正文塞进上下文既浪费 token 也会稀释重点。
 */
public record KnowledgeHitRes(
        String articleId,
        String title,
        int chunkIndex,
        String content,
        double score) {
}
