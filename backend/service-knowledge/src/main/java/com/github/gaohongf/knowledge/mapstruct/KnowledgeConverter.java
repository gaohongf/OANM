package com.github.gaohongf.knowledge.mapstruct;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.github.gaohongf.knowledge.entity.po.KnowledgeArticleEntity;
import com.github.gaohongf.knowledge.entity.po.KnowledgeArticleRevisionEntity;
import com.github.gaohongf.knowledge.entity.po.KnowledgeCategoryEntity;
import com.github.gaohongf.knowledge.entity.res.KnowledgeArticleDetailRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeArticleRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeCategoryRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeManageArticleRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeManageDetailRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeRevisionRes;
import com.github.gaohongf.knowledge.entity.res.KnowledgeSearchItemRes;

/**
 * 实体 → 响应。
 *
 * <h2>id 的 Long → String 与枚举 → String 都是内建转换</h2>
 * 雪花 id 必须以字符串出网；枚举落库用 EnumTypeHandler 存的也是 name()，
 * 所以"库里存什么、接口吐什么、前端比对什么"是同一种表示。
 *
 * <h2>派生字段在 service 里补</h2>
 * categoryName、publishedCount、score 在实体上不存在，
 * 这里用 IGNORE 策略跳过，由 service 组装。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KnowledgeConverter {

    KnowledgeCategoryRes toCategoryRes(KnowledgeCategoryEntity entity);

    List<KnowledgeCategoryRes> toCategoryResList(List<KnowledgeCategoryEntity> entities);

    KnowledgeArticleRes toRes(KnowledgeArticleEntity entity);

    List<KnowledgeArticleRes> toResList(List<KnowledgeArticleEntity> entities);

    KnowledgeArticleDetailRes toDetailRes(KnowledgeArticleEntity entity);

    KnowledgeManageArticleRes toManageRes(KnowledgeArticleEntity entity);

    List<KnowledgeManageArticleRes> toManageResList(List<KnowledgeArticleEntity> entities);

    KnowledgeManageDetailRes toManageDetailRes(KnowledgeArticleEntity entity);

    KnowledgeSearchItemRes toSearchItemRes(KnowledgeArticleEntity entity);

    KnowledgeRevisionRes toRevisionRes(KnowledgeArticleRevisionEntity entity);

    List<KnowledgeRevisionRes> toRevisionResList(List<KnowledgeArticleRevisionEntity> entities);
}
