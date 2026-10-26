package com.github.gaohongf.knowledge.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.github.gaohongf.knowledge.config.KnowledgeAsyncConfiguration;
import com.github.gaohongf.knowledge.config.KnowledgeProperties;
import com.github.gaohongf.knowledge.dao.KnowledgeArticleDao;
import com.github.gaohongf.knowledge.entity.po.ArticleStatus;
import com.github.gaohongf.knowledge.entity.po.KnowledgeArticleEntity;
import com.github.gaohongf.knowledge.repository.ChunkRow;
import com.github.gaohongf.knowledge.repository.KnowledgeIndexRepository;
import com.github.gaohongf.knowledge.service.KnowledgeIndexService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 分块 → 嵌入 → 替换 chunks。
 *
 * <h2>先算成功，再替换</h2>
 * 不是"先删旧 chunks 再嵌入"：嵌入失败时旧索引必须原样保留，
 * 否则一篇本来能搜到的文章会因为一次失败的重新索引而消失。
 *
 * <h2>版本围栏</h2>
 * 开始嵌入时记下文章的 {@code version}；写回前用它做条件更新。
 * 嵌入的几秒内文章被改过就丢弃本次结果（0 行），由那次修改触发的新一轮重建负责。
 * 顺带地，这个条件更新也锁住了文章行，让同一篇文章的并发重建串行化。
 *
 * <h2>为什么每块前置标题</h2>
 * Spring AI 1.1.8 的 TokenTextSplitter 不支持重叠切片，标题往往只出现在第一块；
 * 每块嵌入时拼上标题，可以补偿跨块的上下文丢失。库里存的仍是原始分块。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIndexServiceImpl implements KnowledgeIndexService {

    private static final TokenTextSplitter SPLITTER = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build();

    private static final int MAX_ERROR_LENGTH = 500;

    private final KnowledgeArticleDao articleDao;
    private final KnowledgeIndexRepository indexRepository;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Async(KnowledgeAsyncConfiguration.INDEX_EXECUTOR)
    public void rebuildAsync(Long articleId) {
        KnowledgeArticleEntity article = articleDao.selectById(articleId);
        if (article == null) {
            // 逻辑删除后 selectById 返回 null；chunks 已在删除事务里清掉了
            indexRepository.deleteChunks(articleId);
            return;
        }

        int fence = article.getVersion() == null ? 0 : article.getVersion();
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            discard(articleId, fence);
            return;
        }

        List<ChunkRow> chunks;
        try {
            chunks = buildChunks(article);
        } catch (RuntimeException failure) {
            log.warn("知识库文章 {} 分块或嵌入失败: {}", articleId, rootMessage(failure));
            markFailed(articleId, fence, failure);
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (indexRepository.markReady(articleId, fence) == 0) {
                    log.info("知识库文章 {} 在索引期间被修改，丢弃本次索引结果", articleId);
                    return;
                }
                indexRepository.deleteChunks(articleId);
                indexRepository.insertChunks(articleId, chunks);
            });
        } catch (RuntimeException failure) {
            log.error("知识库文章 {} 写入索引失败: {}", articleId, rootMessage(failure));
            markFailed(articleId, fence, failure);
        }
    }

    @Override
    public void markQueueRejected(Long articleId, String reason) {
        try {
            indexRepository.markFailedWithoutFence(articleId, truncate(reason));
        } catch (RuntimeException failure) {
            log.error("知识库文章 {} 标记索引队列拒绝失败", articleId, failure);
        }
    }

    /** 文章已不是发布态：围栏通过就删掉残留的 chunks */
    private void discard(Long articleId, int fence) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (indexRepository.resetToNone(articleId, fence) == 1) {
                    indexRepository.deleteChunks(articleId);
                }
            });
        } catch (RuntimeException failure) {
            log.warn("知识库文章 {} 清理索引失败: {}", articleId, rootMessage(failure));
        }
    }

    private List<ChunkRow> buildChunks(KnowledgeArticleEntity article) {
        List<Document> parts = SPLITTER.split(new Document(article.getContent()))
                .stream()
                .filter(part -> StringUtils.hasText(part.getText()))
                .toList();
        if (parts.isEmpty()) {
            throw new IllegalStateException("正文没有可索引的内容");
        }

        List<String> texts = parts.stream()
                .map(part -> article.getTitle() + "\n" + part.getText())
                .toList();
        List<float[]> vectors = embed(texts);

        List<ChunkRow> chunks = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            chunks.add(new ChunkRow(i, parts.get(i).getText(), vectors.get(i)));
        }
        return chunks;
    }

    /**
     * 分批嵌入。
     *
     * <p>
     * {@code EmbeddingModel.embed(List)} 只发一次请求，不会自动分批；一次塞太多容易超时，
     * 这里按配置的批大小逐批调用。每批都校验返回数量与向量维度 ——
     * 维度对不上时宁可让索引失败并写明原因，也不要写进一张维度错的表。
     */
    private List<float[]> embed(List<String> texts) {
        int batchSize = Math.max(1, properties.getEmbeddingBatchSize());
        int expectedDimensions = properties.getExpectedDimensions();
        List<float[]> vectors = new ArrayList<>(texts.size());

        for (int from = 0; from < texts.size(); from += batchSize) {
            List<String> batch = texts.subList(from, Math.min(from + batchSize, texts.size()));
            List<float[]> batchVectors = embeddingModel.embed(batch);
            if (batchVectors == null || batchVectors.size() != batch.size()) {
                throw new IllegalStateException("嵌入返回数量不一致：期望 " + batch.size()
                        + "，实际 " + (batchVectors == null ? 0 : batchVectors.size()));
            }
            for (float[] vector : batchVectors) {
                if (vector == null || vector.length != expectedDimensions) {
                    throw new IllegalStateException("嵌入维度不一致：期望 " + expectedDimensions
                            + "，实际 " + (vector == null ? 0 : vector.length));
                }
                vectors.add(vector);
            }
        }
        return vectors;
    }

    private void markFailed(Long articleId, int fence, RuntimeException failure) {
        try {
            indexRepository.markFailed(articleId, fence, truncate(rootMessage(failure)));
        } catch (RuntimeException nested) {
            log.error("知识库文章 {} 标记索引失败状态也失败", articleId, nested);
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
                break;
            }
            current = current.getCause();
        }
        return message != null ? message : failure.getClass().getSimpleName();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
