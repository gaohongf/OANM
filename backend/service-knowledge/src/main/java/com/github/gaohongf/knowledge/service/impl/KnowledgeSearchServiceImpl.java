package com.github.gaohongf.knowledge.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.knowledge.config.KnowledgeProperties;
import com.github.gaohongf.knowledge.dao.KnowledgeArticleDao;
import com.github.gaohongf.knowledge.dao.KnowledgeCategoryDao;
import com.github.gaohongf.knowledge.entity.po.ArticleStatus;
import com.github.gaohongf.knowledge.entity.po.KnowledgeArticleEntity;
import com.github.gaohongf.knowledge.entity.po.KnowledgeCategoryEntity;
import com.github.gaohongf.knowledge.entity.req.RetrieveCommand;
import com.github.gaohongf.knowledge.entity.res.KnowledgeHitRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeRetrieveRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeSearchItemRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeSearchRes;
import com.github.gaohongf.knowledge.mapstruct.KnowledgeConverter;
import com.github.gaohongf.knowledge.repository.ChunkHitRow;
import com.github.gaohongf.knowledge.repository.KnowledgeIndexRepository;
import com.github.gaohongf.knowledge.rsm.KnowledgeRsm;
import com.github.gaohongf.knowledge.service.KnowledgeSearchService;
import com.github.gaohongf.knowledge.support.KnowledgeKeywords;
import com.lingyun.base.rsm.R;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 语义检索与关键字补足。
 *
 * <h2>页面搜索：语义优先、关键字补足</h2>
 * 先向量召回，按文章聚合取最高分片段；不足 topN 时再用 ILIKE 补齐。
 * 嵌入模型不可用时整体降级为关键字，响应里的 {@code mode} 会告诉前端发生了降级 ——
 * 搜索框报错比返回少几条结果更糟。
 *
 * <h2>过采样</h2>
 * HNSW 先取近邻再过滤状态/分类，过滤会损失召回，所以按 topK 的倍数多取一些，
 * 在应用层再聚合、筛选。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {

    private static final String MODE_SEMANTIC = "SEMANTIC";
    private static final String MODE_KEYWORD = "KEYWORD";

    /** 即使 topK 很小也至少取这么多近邻，否则过滤后可能一篇都不剩 */
    private static final int MIN_OVERFETCH = 30;

    private static final int MAX_RETRIEVE_TOP_K = 50;

    private final EmbeddingModel embeddingModel;
    private final KnowledgeIndexRepository indexRepository;
    private final KnowledgeArticleDao articleDao;
    private final KnowledgeCategoryDao categoryDao;
    private final KnowledgeConverter converter;
    private final KnowledgeProperties properties;

    @Override
    public KnowledgeSearchRes search(String query, Integer limit, Long categoryId) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty()) {
            R.error(KnowledgeRsm.KNOWLEDGE_QUERY_REQUIRED);
        }

        int topK = resolveSearchLimit(limit);
        List<KnowledgeSearchItemRes> items;
        String mode = MODE_SEMANTIC;
        try {
            items = semanticItems(keyword, categoryId, topK);
        } catch (RuntimeException failure) {
            log.warn("知识库语义检索不可用，降级为关键字检索: {}", failure.getMessage());
            items = new ArrayList<>();
            mode = MODE_KEYWORD;
        }

        if (items.size() < topK) {
            Set<Long> excluded = items.stream()
                    .map(item -> Long.valueOf(item.getId()))
                    .collect(Collectors.toSet());
            items.addAll(keywordItems(keyword, categoryId, topK - items.size(), excluded));
        }
        return new KnowledgeSearchRes(mode, items);
    }

    @Override
    public KnowledgeRetrieveRes retrieve(RetrieveCommand command) {
        String query = command.getQuery() == null ? "" : command.getQuery().trim();
        if (query.isEmpty()) {
            R.error(KnowledgeRsm.KNOWLEDGE_QUERY_REQUIRED);
        }

        int topK = command.getTopK() == null ? properties.getRetrieveTopK() : command.getTopK();
        topK = Math.max(1, Math.min(topK, MAX_RETRIEVE_TOP_K));

        try {
            float[] queryVector = embedOne(query);
            List<ChunkHitRow> hits = indexRepository.search(queryVector, command.getCategoryId(), topK);
            if (hits.isEmpty()) {
                return new KnowledgeRetrieveRes(List.of());
            }

            Map<Long, String> titles = loadTitles(
                    hits.stream().map(ChunkHitRow::articleId).collect(Collectors.toSet()));
            List<KnowledgeHitRes> records = hits.stream()
                    .map(hit -> new KnowledgeHitRes(
                            String.valueOf(hit.articleId()),
                            titles.getOrDefault(hit.articleId(), ""),
                            hit.chunkIndex(),
                            hit.content(),
                            hit.score()))
                    .toList();
            return new KnowledgeRetrieveRes(records);
        } catch (RuntimeException failure) {
            // 503 而不是空列表：调用方需要能区分"没有相关内容"和"检索服务挂了"
            log.warn("知识库内部检索失败: {}", failure.getMessage());
            R.error(KnowledgeRsm.KNOWLEDGE_RETRIEVE_UNAVAILABLE);
            return new KnowledgeRetrieveRes(List.of());
        }
    }

    // ------------------------------------------------------------------
    // 语义
    // ------------------------------------------------------------------

    private List<KnowledgeSearchItemRes> semanticItems(String keyword, Long categoryId, int topK) {
        float[] queryVector = embedOne(keyword);
        int overfetch = Math.max(topK * Math.max(1, properties.getSearchOverfetch()), MIN_OVERFETCH);
        List<ChunkHitRow> hits = indexRepository.search(queryVector, categoryId, overfetch);

        // 同一篇文章可能命中多个片段，只保留最高分
        Map<Long, Double> bestScores = new LinkedHashMap<>();
        for (ChunkHitRow hit : hits) {
            bestScores.merge(hit.articleId(), hit.score(), Math::max);
        }

        double minScore = properties.getMinScore();
        List<Map.Entry<Long, Double>> ranked = bestScores.entrySet().stream()
                .filter(entry -> entry.getValue() >= minScore)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .toList();
        if (ranked.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> ids = ranked.stream().map(Map.Entry::getKey).toList();
        Map<Long, KnowledgeArticleEntity> articles = loadPublishedArticles(ids);
        Map<Long, String> categoryNames = loadCategoryNames();

        List<KnowledgeSearchItemRes> items = new ArrayList<>(ranked.size());
        for (Map.Entry<Long, Double> entry : ranked) {
            KnowledgeArticleEntity article = articles.get(entry.getKey());
            if (article == null) {
                // 召回之后被取消发布或删除
                continue;
            }
            KnowledgeSearchItemRes item = converter.toSearchItemRes(article);
            item.setCategoryName(categoryNames.get(article.getCategoryId()));
            item.setScore(entry.getValue());
            items.add(item);
        }
        return items;
    }

    // ------------------------------------------------------------------
    // 关键字
    // ------------------------------------------------------------------

    private List<KnowledgeSearchItemRes> keywordItems(String keyword, Long categoryId, int remaining,
                                                      Set<Long> excluded) {
        if (remaining <= 0) {
            return List.of();
        }

        LambdaQueryWrapper<KnowledgeArticleEntity> wrapper = Wrappers.<KnowledgeArticleEntity>lambdaQuery()
                .select(KnowledgeArticleEntity::getId, KnowledgeArticleEntity::getTitle,
                        KnowledgeArticleEntity::getSummary, KnowledgeArticleEntity::getCategoryId,
                        KnowledgeArticleEntity::getPublishTime, KnowledgeArticleEntity::getUpdateTime,
                        KnowledgeArticleEntity::getViewCount)
                .eq(KnowledgeArticleEntity::getStatus, ArticleStatus.PUBLISHED)
                .eq(categoryId != null, KnowledgeArticleEntity::getCategoryId, categoryId)
                .notIn(!excluded.isEmpty(), KnowledgeArticleEntity::getId, excluded)
                .orderByDesc(KnowledgeArticleEntity::getUpdateTime)
                .orderByDesc(KnowledgeArticleEntity::getId);
        KnowledgeKeywords.apply(wrapper, keyword);
        // remaining 是本地计算的整数，不来自请求参数；last() 用来补 LIMIT
        wrapper.last("LIMIT " + remaining);

        return toKeywordItems(articleDao.selectList(wrapper));
    }

    private List<KnowledgeSearchItemRes> toKeywordItems(List<KnowledgeArticleEntity> articles) {
        Map<Long, String> categoryNames = loadCategoryNames();
        return articles.stream().map(article -> {
            KnowledgeSearchItemRes item = converter.toSearchItemRes(article);
            item.setCategoryName(categoryNames.get(article.getCategoryId()));
            return item;
        }).toList();
    }

    // ------------------------------------------------------------------
    // 公共
    // ------------------------------------------------------------------

    private float[] embedOne(String text) {
        float[] vector = embeddingModel.embed(text);
        if (vector == null || vector.length != properties.getExpectedDimensions()) {
            throw new IllegalStateException("嵌入维度不一致：期望 " + properties.getExpectedDimensions()
                    + "，实际 " + (vector == null ? 0 : vector.length));
        }
        return vector;
    }

    private Map<Long, KnowledgeArticleEntity> loadPublishedArticles(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return articleDao.selectList(Wrappers.<KnowledgeArticleEntity>lambdaQuery()
                        .select(KnowledgeArticleEntity::getId, KnowledgeArticleEntity::getTitle,
                                KnowledgeArticleEntity::getSummary, KnowledgeArticleEntity::getCategoryId,
                                KnowledgeArticleEntity::getPublishTime, KnowledgeArticleEntity::getUpdateTime,
                                KnowledgeArticleEntity::getViewCount)
                        .in(KnowledgeArticleEntity::getId, ids)
                        .eq(KnowledgeArticleEntity::getStatus, ArticleStatus.PUBLISHED))
                .stream()
                .collect(Collectors.toMap(KnowledgeArticleEntity::getId, article -> article, (a, b) -> a));
    }

    private Map<Long, String> loadTitles(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return articleDao.selectList(Wrappers.<KnowledgeArticleEntity>lambdaQuery()
                        .select(KnowledgeArticleEntity::getId, KnowledgeArticleEntity::getTitle)
                        .in(KnowledgeArticleEntity::getId, ids)
                        .eq(KnowledgeArticleEntity::getStatus, ArticleStatus.PUBLISHED))
                .stream()
                .collect(Collectors.toMap(KnowledgeArticleEntity::getId, KnowledgeArticleEntity::getTitle));
    }

    private Map<Long, String> loadCategoryNames() {
        return categoryDao.selectList(Wrappers.<KnowledgeCategoryEntity>lambdaQuery())
                .stream()
                .collect(Collectors.toMap(KnowledgeCategoryEntity::getId, KnowledgeCategoryEntity::getName));
    }

    private int resolveSearchLimit(Integer limit) {
        int max = Math.max(1, properties.getSearchLimit());
        if (limit == null) {
            return max;
        }
        return Math.max(1, Math.min(limit, max));
    }
}
