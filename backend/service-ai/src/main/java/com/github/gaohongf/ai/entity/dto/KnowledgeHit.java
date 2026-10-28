package com.github.gaohongf.ai.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库内部检索返回的一个片段。
 *
 * <p>
 * 与 service-knowledge 的 {@code KnowledgeHitRes} 字段一一对应。这里不直接复用那个类：
 * 跨服务共享实体会把两边的发布绑在一起。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeHit {

    private String articleId;

    private String title;

    private int chunkIndex;

    private String content;

    /** 余弦相似度，越大越相关 */
    private double score;
}
