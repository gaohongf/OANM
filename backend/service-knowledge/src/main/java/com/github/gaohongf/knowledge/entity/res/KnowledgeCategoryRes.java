package com.github.gaohongf.knowledge.entity.res;

import lombok.Data;

/** 知识库分类 */
@Data
public class KnowledgeCategoryRes {

    /** 雪花 id 以字符串出网，避免 JS Number 丢精度 */
    private String id;

    private String name;

    private Integer sort;

    /** 该分类下已发布的文章数；管理端分类维护不使用这个字段 */
    private long publishedCount;
}
