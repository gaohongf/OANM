package com.github.gaohongf.knowledge.entity.res;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识管理详情 = 管理列表行 + Markdown 正文（编辑表单回填用） */
@EqualsAndHashCode(callSuper = true)
@Data
public class KnowledgeManageDetailRes extends KnowledgeManageArticleRes {

    private String content;
}
