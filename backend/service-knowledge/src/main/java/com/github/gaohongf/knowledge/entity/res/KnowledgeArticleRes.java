package com.github.gaohongf.knowledge.entity.res;

import java.time.LocalDateTime;

import lombok.Data;

/** 知识库列表行（不含正文） */
@Data
public class KnowledgeArticleRes {

    private String id;

    private String title;

    private String summary;

    private String categoryId;

    private String categoryName;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;

    private long viewCount;
}
