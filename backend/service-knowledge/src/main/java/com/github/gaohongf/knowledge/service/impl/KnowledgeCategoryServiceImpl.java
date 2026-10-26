package com.github.gaohongf.knowledge.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.knowledge.dao.KnowledgeArticleDao;
import com.github.gaohongf.knowledge.dao.KnowledgeCategoryDao;
import com.github.gaohongf.knowledge.entity.po.ArticleStatus;
import com.github.gaohongf.knowledge.entity.po.KnowledgeArticleEntity;
import com.github.gaohongf.knowledge.entity.po.KnowledgeCategoryEntity;
import com.github.gaohongf.knowledge.entity.req.SaveCategoryCommand;
import com.github.gaohongf.knowledge.entity.res.KnowledgeCategoryRes;
import com.github.gaohongf.knowledge.mapstruct.KnowledgeConverter;
import com.github.gaohongf.knowledge.rsm.KnowledgeRsm;
import com.github.gaohongf.knowledge.service.KnowledgeCategoryService;
import com.lingyun.base.rsm.R;

import lombok.AllArgsConstructor;

/**
 * 分类维护。
 *
 * <h2>为什么删除要先数文章</h2>
 * 没有外键（逻辑删除的表加外键也拦不住软删行），只能在应用层判断。
 * 这里把草稿也算进去：隐藏分类后，那些草稿仍然指着它。
 */
@AllArgsConstructor
@Service
public class KnowledgeCategoryServiceImpl implements KnowledgeCategoryService {

    private final KnowledgeCategoryDao categoryDao;
    private final KnowledgeArticleDao articleDao;
    private final KnowledgeConverter converter;

    @Override
    public List<KnowledgeCategoryRes> list() {
        List<KnowledgeCategoryEntity> entities = categoryDao.selectList(
                Wrappers.<KnowledgeCategoryEntity>lambdaQuery()
                        .orderByAsc(KnowledgeCategoryEntity::getSort)
                        .orderByAsc(KnowledgeCategoryEntity::getId));
        List<KnowledgeCategoryRes> records = converter.toCategoryResList(entities);
        if (records.isEmpty()) {
            return records;
        }

        // 一次查出全部已发布文章的分类，避免每个分类各查一次
        Map<Long, Long> publishedCounts = articleDao.selectList(
                        Wrappers.<KnowledgeArticleEntity>lambdaQuery()
                                .select(KnowledgeArticleEntity::getId, KnowledgeArticleEntity::getCategoryId)
                                .eq(KnowledgeArticleEntity::getStatus, ArticleStatus.PUBLISHED))
                .stream()
                .collect(Collectors.groupingBy(KnowledgeArticleEntity::getCategoryId, Collectors.counting()));

        records.forEach(res -> res.setPublishedCount(
                publishedCounts.getOrDefault(Long.valueOf(res.getId()), 0L)));
        return records;
    }

    @Override
    public Long create(SaveCategoryCommand command) {
        String name = command.getName().trim();
        requireUniqueName(name, null);

        KnowledgeCategoryEntity entity = new KnowledgeCategoryEntity();
        entity.setName(name);
        entity.setSort(command.getSort());
        try {
            categoryDao.insert(entity);
        } catch (DuplicateKeyException duplicated) {
            // 预检查只能挡住顺序提交；并发提交时靠部分唯一索引兜底
            R.error(KnowledgeRsm.KNOWLEDGE_CATEGORY_EXISTS);
        }
        return entity.getId();
    }

    @Override
    public void update(Long id, SaveCategoryCommand command) {
        KnowledgeCategoryEntity current = categoryDao.selectById(id);
        if (current == null) {
            R.error(KnowledgeRsm.KNOWLEDGE_CATEGORY_NOT_FOUND);
        }

        String name = command.getName().trim();
        requireUniqueName(name, id);
        current.setName(name);
        current.setSort(command.getSort());

        int rows;
        try {
            rows = categoryDao.updateById(current);
        } catch (DuplicateKeyException duplicated) {
            R.error(KnowledgeRsm.KNOWLEDGE_CATEGORY_EXISTS);
            return;
        }
        if (rows == 0) {
            R.error(KnowledgeRsm.KNOWLEDGE_CONCURRENT_MODIFIED);
        }
    }

    @Override
    public void delete(Long id) {
        KnowledgeCategoryEntity current = categoryDao.selectById(id);
        if (current == null) {
            R.error(KnowledgeRsm.KNOWLEDGE_CATEGORY_NOT_FOUND);
        }

        // 逻辑删除的行已被 @TableLogic 排除，这里数到的都是"还在用"的文章
        Long used = articleDao.selectCount(Wrappers.<KnowledgeArticleEntity>lambdaQuery()
                .eq(KnowledgeArticleEntity::getCategoryId, id));
        if (used != null && used > 0) {
            R.error(KnowledgeRsm.KNOWLEDGE_CATEGORY_IN_USE);
        }
        categoryDao.deleteById(id);
    }

    private void requireUniqueName(String name, Long excludeId) {
        Long count = categoryDao.selectCount(Wrappers.<KnowledgeCategoryEntity>lambdaQuery()
                .eq(KnowledgeCategoryEntity::getName, name)
                .ne(excludeId != null, KnowledgeCategoryEntity::getId, excludeId));
        if (count != null && count > 0) {
            R.error(KnowledgeRsm.KNOWLEDGE_CATEGORY_EXISTS);
        }
    }
}
