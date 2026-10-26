package com.github.gaohongf.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 知识库的可调参数。
 *
 * <p>
 * {@code expectedDimensions} 必须与 {@code spring.ai.ollama.embedding.options.dimensions}
 * 以及建表脚本里的 {@code vector(2000)} 三处一致。代码在索引时校验实际向量长度，
 * 不一致会直接失败并写入 {@code index_error}，而不是悄悄写进一张维度不对的表。
 */
@Data
@ConfigurationProperties(prefix = "oanm.knowledge")
public class KnowledgeProperties {

    /** 期望的嵌入维度 */
    private int expectedDimensions = 2000;

    /** 每批送去嵌入的文本条数（EmbeddingModel.embed(List) 不会自动分批） */
    private int embeddingBatchSize = 16;

    /** HNSW 近邻的过采样倍数 */
    private int searchOverfetch = 3;

    /** 页面搜索最多返回的篇数 */
    private int searchLimit = 20;

    /** 内部检索接口的默认 topK */
    private int retrieveTopK = 5;

    /** 余弦相似度下限，低于它的片段不参与页面搜索 */
    private double minScore = 0.2;
}
