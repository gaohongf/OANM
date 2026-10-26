package com.github.gaohongf.knowledge.entity.res;

import java.time.LocalDateTime;

import lombok.Data;

/** 知识管理列表行：在公开列表字段之外带上状态与索引状态 */
@Data
public class KnowledgeManageArticleRes {

    private String id;

    private String title;

    private String summary;

    private String categoryId;

    private String categoryName;

    /** DRAFT / PUBLISHED */
    private String status;

    /** NONE / INDEXING / READY / FAILED */
    private String indexStatus;

    /** 最近一次索引失败原因；成功时为 null */
    private String indexError;

    private LocalDateTime indexedAt;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;

    private long viewCount;
}
