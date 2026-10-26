package com.github.gaohongf.knowledge.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.gaohongf.knowledge.dao.KnowledgeArticleDao;
import com.github.gaohongf.knowledge.dao.KnowledgeArticleRevisionDao;
import com.github.gaohongf.knowledge.dao.KnowledgeCategoryDao;
import com.github.gaohongf.knowledge.entity.po.ArticleStatus;
import com.github.gaohongf.knowledge.entity.po.IndexStatus;
import com.github.gaohongf.knowledge.entity.po.KnowledgeArticleEntity;
import com.github.gaohongf.knowledge.entity.po.KnowledgeArticleRevisionEntity;
import com.github.gaohongf.knowledge.entity.po.KnowledgeCategoryEntity;
import com.github.gaohongf.knowledge.entity.po.RevisionChangeType;
import com.github.gaohongf.knowledge.entity.req.SaveArticleCommand;
import com.github.gaohongf.knowledge.entity.res.KnowledgeArticleDetailRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeArticleRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeManageArticleRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeManageDetailRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeRevisionRes;
import com.github.gaohongf.knowledge.event.KnowledgeArticleChangedEvent;
import com.github.gaohongf.knowledge.mapstruct.KnowledgeConverter;
import com.github.gaohongf.knowledge.repository.KnowledgeIndexRepository;
import com.github.gaohongf.knowledge.rsm.KnowledgeRsm;
import com.github.gaohongf.knowledge.service.KnowledgeArticleService;
import com.github.gaohongf.knowledge.support.KnowledgeKeywords;
import com.github.gaohongf.model.PageRes;
import com.lingyun.base.rsm.R;

import lombok.AllArgsConstructor;

/**
 * 文章读写、发布与修订。
 *
 * <h2>索引不在保存事务里做</h2>
 * 保存只把 {@code index_status} 置为 INDEXING，提交后由监听器丢进异步线程池。
 * 本机 4B 模型一次嵌入要几秒，放进事务会一直占着行锁和数据库连接，
 * Ollama 超时还会把保存一起回滚。
 *
 * <h2>每次保存都写修订快照</h2>
 * 新建时也写第 1 条，因此"当前文章 == revision_no 最大的那一条"。
 * 状态变更不写修订 —— 修订是内容历史，不是操作日志。
 */
@AllArgsConstructor
@Service
public class KnowledgeArticleServiceImpl implements KnowledgeArticleService {

    private final KnowledgeArticleDao articleDao;
    private final KnowledgeArticleRevisionDao revisionDao;
    private final KnowledgeCategoryDao categoryDao;
    private final KnowledgeIndexRepository indexRepository;
    private final KnowledgeConverter converter;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------
    // 读取
    // ------------------------------------------------------------------

    @Override
    public PageRes<KnowledgeArticleRes> pagePublic(long current, long size, Long categoryId,
                                                  String keyword) {
        long safeCurrent = PageRes.clampCurrent(current);
        long safeSize = PageRes.clampSize(size);
        String search = KnowledgeKeywords.normalize(keyword);

        LambdaQueryWrapper<KnowledgeArticleEntity> wrapper = Wrappers.<KnowledgeArticleEntity>lambdaQuery()
                .select(KnowledgeArticleEntity::getId, KnowledgeArticleEntity::getTitle,
                        KnowledgeArticleEntity::getSummary, KnowledgeArticleEntity::getCategoryId,
                        KnowledgeArticleEntity::getPublishTime, KnowledgeArticleEntity::getUpdateTime,
                        KnowledgeArticleEntity::getViewCount)
                .eq(KnowledgeArticleEntity::getStatus, ArticleStatus.PUBLISHED)
                .eq(categoryId != null, KnowledgeArticleEntity::getCategoryId, categoryId)
                .orderByDesc(KnowledgeArticleEntity::getUpdateTime)
                .orderByDesc(KnowledgeArticleEntity::getId);
        KnowledgeKeywords.apply(wrapper, search);

        IPage<KnowledgeArticleEntity> page = articleDao.selectPage(
                new Page<>(safeCurrent, safeSize), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(), List.of());
        }

        Map<Long, String> categoryNames = loadCategoryNames(page.getRecords());
        List<KnowledgeArticleRes> records = page.getRecords().stream().map(entity -> {
            KnowledgeArticleRes res = converter.toRes(entity);
            res.setCategoryName(categoryNames.get(entity.getCategoryId()));
            return res;
        }).toList();

        return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    public KnowledgeArticleDetailRes findPublicDetail(Long id) {
        KnowledgeArticleEntity entity = articleDao.selectById(id);
        if (entity == null || entity.getStatus() != ArticleStatus.PUBLISHED) {
            R.error(KnowledgeRsm.KNOWLEDGE_ARTICLE_NOT_FOUND);
        }

        // 计数是 view_count + 1 的原子自增；走 updateById 会把读到的旧值写回去。
        // 条件里再带一次状态：读完之后刚好被取消发布时不要照样加。
        articleDao.update(null, Wrappers.<KnowledgeArticleEntity>lambdaUpdate()
                .eq(KnowledgeArticleEntity::getId, id)
                .eq(KnowledgeArticleEntity::getStatus, ArticleStatus.PUBLISHED)
                .setSql("view_count = view_count + 1"));

        KnowledgeArticleDetailRes res = converter.toDetailRes(entity);
        res.setCategoryName(findCategoryName(entity.getCategoryId()));
        res.setViewCount(entity.getViewCount() == null ? 1L : entity.getViewCount() + 1);
        return res;
    }

    @Override
    public PageRes<KnowledgeManageArticleRes> pageManage(long current, long size, Long categoryId,
                                                         String status, String keyword) {
        long safeCurrent = PageRes.clampCurrent(current);
        long safeSize = PageRes.clampSize(size);
        ArticleStatus statusFilter = parseStatus(status, null);
        String search = KnowledgeKeywords.normalize(keyword);

        LambdaQueryWrapper<KnowledgeArticleEntity> wrapper = Wrappers.<KnowledgeArticleEntity>lambdaQuery()
                .select(KnowledgeArticleEntity::getId, KnowledgeArticleEntity::getTitle,
                        KnowledgeArticleEntity::getSummary, KnowledgeArticleEntity::getCategoryId,
                        KnowledgeArticleEntity::getStatus, KnowledgeArticleEntity::getIndexStatus,
                        KnowledgeArticleEntity::getIndexError, KnowledgeArticleEntity::getIndexedAt,
                        KnowledgeArticleEntity::getPublishTime, KnowledgeArticleEntity::getUpdateTime,
                        KnowledgeArticleEntity::getViewCount)
                .eq(statusFilter != null, KnowledgeArticleEntity::getStatus, statusFilter)
                .eq(categoryId != null, KnowledgeArticleEntity::getCategoryId, categoryId)
                .orderByDesc(KnowledgeArticleEntity::getUpdateTime)
                .orderByDesc(KnowledgeArticleEntity::getId);
        KnowledgeKeywords.apply(wrapper, search);

        IPage<KnowledgeArticleEntity> page = articleDao.selectPage(
                new Page<>(safeCurrent, safeSize), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(), List.of());
        }

        Map<Long, String> categoryNames = loadCategoryNames(page.getRecords());
        List<KnowledgeManageArticleRes> records = page.getRecords().stream().map(entity -> {
            KnowledgeManageArticleRes res = converter.toManageRes(entity);
            res.setCategoryName(categoryNames.get(entity.getCategoryId()));
            return res;
        }).toList();

        return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    public KnowledgeManageDetailRes findManageDetail(Long id) {
        KnowledgeArticleEntity entity = requireArticle(id);
        KnowledgeManageDetailRes res = converter.toManageDetailRes(entity);
        res.setCategoryName(findCategoryName(entity.getCategoryId()));
        return res;
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Long create(SaveArticleCommand command) {
        String title = command.getTitle().trim();
        requireCategory(command.getCategoryId());
        requireUniqueTitle(command.getCategoryId(), title, null);

        ArticleStatus status = parseStatus(command.getStatus(), ArticleStatus.DRAFT);
        boolean publish = status == ArticleStatus.PUBLISHED;

        KnowledgeArticleEntity entity = new KnowledgeArticleEntity();
        entity.setTitle(title);
        entity.setSummary(trimToNull(command.getSummary()));
        entity.setContent(command.getContent());
        entity.setCategoryId(command.getCategoryId());
        entity.setStatus(status);
        entity.setIndexStatus(publish ? IndexStatus.INDEXING : IndexStatus.NONE);
        entity.setViewCount(0L);
        if (publish) {
            entity.setPublishTime(LocalDateTime.now());
        }
        try {
            articleDao.insert(entity);
        } catch (DuplicateKeyException duplicated) {
            R.error(KnowledgeRsm.KNOWLEDGE_ARTICLE_TITLE_EXISTS);
        }

        appendRevision(entity, RevisionChangeType.CREATE, null);
        if (publish) {
            publishIndexEvent(entity.getId());
        }
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(Long id, SaveArticleCommand command) {
        KnowledgeArticleEntity current = requireArticle(id);
        ArticleStatus status = parseStatus(command.getStatus(), current.getStatus());
        boolean publish = status == ArticleStatus.PUBLISHED;

        String title = command.getTitle().trim();
        requireCategory(command.getCategoryId());
        requireUniqueTitle(command.getCategoryId(), title, id);

        current.setTitle(title);
        current.setSummary(trimToNull(command.getSummary()));
        current.setContent(command.getContent());
        current.setCategoryId(command.getCategoryId());
        current.setStatus(status);
        if (publish) {
            current.setIndexStatus(IndexStatus.INDEXING);
            if (current.getPublishTime() == null) {
                current.setPublishTime(LocalDateTime.now());
            }
        } else {
            current.setIndexStatus(IndexStatus.NONE);
        }

        int rows;
        try {
            rows = articleDao.updateById(current);
        } catch (DuplicateKeyException duplicated) {
            R.error(KnowledgeRsm.KNOWLEDGE_ARTICLE_TITLE_EXISTS);
            return;
        }
        if (rows == 0) {
            R.error(KnowledgeRsm.KNOWLEDGE_CONCURRENT_MODIFIED);
        }

        appendRevision(current, RevisionChangeType.UPDATE, null);
        if (publish) {
            publishIndexEvent(id);
        } else {
            indexRepository.deleteChunks(id);
        }
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String status) {
        ArticleStatus target = parseStatus(status, null);
        KnowledgeArticleEntity current = requireArticle(id);
        if (current.getStatus() == target) {
            return;
        }

        current.setStatus(target);
        if (target == ArticleStatus.PUBLISHED) {
            current.setIndexStatus(IndexStatus.INDEXING);
            if (current.getPublishTime() == null) {
                current.setPublishTime(LocalDateTime.now());
            }
        } else {
            current.setIndexStatus(IndexStatus.NONE);
        }

        int rows = articleDao.updateById(current);
        if (rows == 0) {
            R.error(KnowledgeRsm.KNOWLEDGE_CONCURRENT_MODIFIED);
        }

        if (target == ArticleStatus.PUBLISHED) {
            publishIndexEvent(id);
        } else {
            // 取消发布后语义检索不能命中；在途的重建会被 markReady 的 status 条件挡掉
            indexRepository.deleteChunks(id);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireArticle(id);
        articleDao.deleteById(id);
        revisionDao.delete(Wrappers.<KnowledgeArticleRevisionEntity>lambdaQuery()
                .eq(KnowledgeArticleRevisionEntity::getArticleId, id));
        // 派生数据物理删除，避免 HNSW 里留墓碑
        indexRepository.deleteChunks(id);
    }

    @Override
    @Transactional
    public void reindex(Long id) {
        KnowledgeArticleEntity current = requireArticle(id);
        if (current.getStatus() != ArticleStatus.PUBLISHED) {
            // 未发布的文章不该有索引；顺手清掉历史残留
            indexRepository.deleteChunks(id);
            if (current.getIndexStatus() != IndexStatus.NONE) {
                current.setIndexStatus(IndexStatus.NONE);
                articleDao.updateById(current);
            }
            return;
        }

        current.setIndexStatus(IndexStatus.INDEXING);
        int rows = articleDao.updateById(current);
        if (rows == 0) {
            R.error(KnowledgeRsm.KNOWLEDGE_CONCURRENT_MODIFIED);
        }
        publishIndexEvent(id);
    }

    // ------------------------------------------------------------------
    // 修订
    // ------------------------------------------------------------------

    @Override
    public List<KnowledgeRevisionRes> listRevisions(Long articleId) {
        requireArticle(articleId);
        // 列表不带正文：快照有整篇 Markdown，一篇文章几十条修订时没必要全读出来；
        // 预览走单独的详情接口
        List<KnowledgeArticleRevisionEntity> revisions = revisionDao.selectList(
                Wrappers.<KnowledgeArticleRevisionEntity>lambdaQuery()
                        .select(KnowledgeArticleRevisionEntity::getId,
                                KnowledgeArticleRevisionEntity::getArticleId,
                                KnowledgeArticleRevisionEntity::getRevisionNo,
                                KnowledgeArticleRevisionEntity::getChangeType,
                                KnowledgeArticleRevisionEntity::getSourceRevisionId,
                                KnowledgeArticleRevisionEntity::getTitle,
                                KnowledgeArticleRevisionEntity::getCategoryId,
                                KnowledgeArticleRevisionEntity::getCreateTime)
                        .eq(KnowledgeArticleRevisionEntity::getArticleId, articleId)
                        .orderByDesc(KnowledgeArticleRevisionEntity::getRevisionNo));
        return converter.toRevisionResList(revisions);
    }

    @Override
    public KnowledgeRevisionRes findRevision(Long articleId, Long revisionId) {
        KnowledgeArticleRevisionEntity revision = revisionDao.selectById(revisionId);
        if (revision == null || !revision.getArticleId().equals(articleId)) {
            R.error(KnowledgeRsm.KNOWLEDGE_REVISION_NOT_FOUND);
        }
        return converter.toRevisionRes(revision);
    }

    @Override
    @Transactional
    public void restore(Long articleId, Long revisionId) {
        KnowledgeArticleEntity current = requireArticle(articleId);
        KnowledgeArticleRevisionEntity revision = revisionDao.selectById(revisionId);
        if (revision == null || !revision.getArticleId().equals(articleId)) {
            R.error(KnowledgeRsm.KNOWLEDGE_REVISION_NOT_FOUND);
        }

        // 分类可能已经被删掉；直接恢复会得到一篇指向不存在分类的文章
        requireCategory(revision.getCategoryId());
        requireUniqueTitle(revision.getCategoryId(), revision.getTitle(), articleId);

        boolean publish = current.getStatus() == ArticleStatus.PUBLISHED;
        current.setTitle(revision.getTitle());
        current.setSummary(revision.getSummary());
        current.setContent(revision.getContent());
        current.setCategoryId(revision.getCategoryId());
        if (publish) {
            // 回滚同样改变了正文，已发布的文章必须重建索引
            current.setIndexStatus(IndexStatus.INDEXING);
        }

        int rows = articleDao.updateById(current);
        if (rows == 0) {
            R.error(KnowledgeRsm.KNOWLEDGE_CONCURRENT_MODIFIED);
        }

        appendRevision(current, RevisionChangeType.ROLLBACK, revisionId);
        if (publish) {
            publishIndexEvent(articleId);
        }
    }

    // ------------------------------------------------------------------
    // 组装与校验
    // ------------------------------------------------------------------

    private KnowledgeArticleEntity requireArticle(Long id) {
        KnowledgeArticleEntity entity = articleDao.selectById(id);
        if (entity == null) {
            R.error(KnowledgeRsm.KNOWLEDGE_ARTICLE_NOT_FOUND);
        }
        return entity;
    }

    private void requireCategory(Long categoryId) {
        if (categoryId == null || categoryDao.selectById(categoryId) == null) {
            R.error(KnowledgeRsm.KNOWLEDGE_CATEGORY_NOT_FOUND);
        }
    }

    private void requireUniqueTitle(Long categoryId, String title, Long excludeId) {
        Long count = articleDao.selectCount(Wrappers.<KnowledgeArticleEntity>lambdaQuery()
                .eq(KnowledgeArticleEntity::getCategoryId, categoryId)
                .eq(KnowledgeArticleEntity::getTitle, title)
                .ne(excludeId != null, KnowledgeArticleEntity::getId, excludeId));
        if (count != null && count > 0) {
            R.error(KnowledgeRsm.KNOWLEDGE_ARTICLE_TITLE_EXISTS);
        }
    }

    /** 追加一条"刚保存的完整状态"；修订号在事务内取 MAX+1，唯一索引兜底并发 */
    private void appendRevision(KnowledgeArticleEntity article, RevisionChangeType changeType,
                                Long sourceRevisionId) {
        KnowledgeArticleRevisionEntity revision = new KnowledgeArticleRevisionEntity();
        revision.setArticleId(article.getId());
        revision.setRevisionNo(nextRevisionNo(article.getId()));
        revision.setTitle(article.getTitle());
        revision.setSummary(article.getSummary());
        revision.setContent(article.getContent());
        revision.setCategoryId(article.getCategoryId());
        revision.setChangeType(changeType);
        revision.setSourceRevisionId(sourceRevisionId);
        try {
            revisionDao.insert(revision);
        } catch (DuplicateKeyException duplicated) {
            R.error(KnowledgeRsm.KNOWLEDGE_CONCURRENT_MODIFIED);
        }
    }

    private int nextRevisionNo(Long articleId) {
        KnowledgeArticleRevisionEntity latest = revisionDao.selectOne(
                Wrappers.<KnowledgeArticleRevisionEntity>lambdaQuery()
                        .eq(KnowledgeArticleRevisionEntity::getArticleId, articleId)
                        .orderByDesc(KnowledgeArticleRevisionEntity::getRevisionNo)
                        .last("LIMIT 1"));
        return latest == null ? 1 : latest.getRevisionNo() + 1;
    }

    private void publishIndexEvent(Long articleId) {
        eventPublisher.publishEvent(new KnowledgeArticleChangedEvent(articleId));
    }

    private Map<Long, String> loadCategoryNames(List<KnowledgeArticleEntity> records) {
        Set<Long> ids = records.stream()
                .map(KnowledgeArticleEntity::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryDao.selectList(Wrappers.<KnowledgeCategoryEntity>lambdaQuery()
                        .in(KnowledgeCategoryEntity::getId, ids))
                .stream()
                .collect(Collectors.toMap(KnowledgeCategoryEntity::getId, KnowledgeCategoryEntity::getName));
    }

    private String findCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        KnowledgeCategoryEntity category = categoryDao.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    private static ArticleStatus parseStatus(String raw, ArticleStatus fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        Optional<ArticleStatus> parsed = ArticleStatus.parse(raw);
        if (parsed.isEmpty()) {
            R.error(KnowledgeRsm.KNOWLEDGE_STATUS_INVALID);
        }
        return parsed.orElse(fallback);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
