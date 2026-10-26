package com.github.gaohongf.knowledge.entity.po;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 知识库文章。
 *
 * <p>
 * {@code status} 与 {@code indexStatus} 是两件事：前者决定文章是否可见，
 * 后者只说明语义索引是否是最新的。索引失败不影响关键字检索。
 */
@EqualsAndHashCode(callSuper = false)
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "knowledge_articles", autoResultMap = true)
public class KnowledgeArticleEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 标题，同一分类下不重复 */
    private String title;

    /** 摘要，列表页展示 */
    private String summary;

    /** Markdown 正文 */
    private String content;

    private Long categoryId;

    private ArticleStatus status;

    private IndexStatus indexStatus;

    /** 最近一次索引失败的原因，管理页 Tooltip 展示 */
    private String indexError;

    private LocalDateTime indexedAt;

    /** 首次发布时间 */
    private LocalDateTime publishTime;

    private Long viewCount;
}
