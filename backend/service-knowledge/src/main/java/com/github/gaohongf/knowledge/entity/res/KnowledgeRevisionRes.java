package com.github.gaohongf.knowledge.entity.res;

import java.time.LocalDateTime;

import lombok.Data;

/** 修订历史行（含正文，便于前端直接预览） */
@Data
public class KnowledgeRevisionRes {

    private String id;

    private String articleId;

    private Integer revisionNo;

    /** CREATE / UPDATE / ROLLBACK */
    private String changeType;

    /** 回滚来源的修订 id；其他类型为 null */
    private String sourceRevisionId;

    private String title;

    private String summary;

    private String content;

    private String categoryId;

    private LocalDateTime createTime;
}
