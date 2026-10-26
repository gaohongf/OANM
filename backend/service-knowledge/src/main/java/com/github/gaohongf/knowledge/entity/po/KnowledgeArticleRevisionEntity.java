package com.github.gaohongf.knowledge.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 文章修订快照：每次保存追加一条完整内容。
 *
 * <p>
 * 不存 status —— 回滚只恢复正文相关内容，发布状态保持不变。
 */
@EqualsAndHashCode(callSuper = false)
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "knowledge_article_revisions", autoResultMap = true)
public class KnowledgeArticleRevisionEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long articleId;

    /** 同一文章内从 1 递增 */
    private Integer revisionNo;

    private String title;

    private String summary;

    private String content;

    private Long categoryId;

    private RevisionChangeType changeType;

    /** 回滚来源的修订 id；其他类型为 null */
    private Long sourceRevisionId;
}
