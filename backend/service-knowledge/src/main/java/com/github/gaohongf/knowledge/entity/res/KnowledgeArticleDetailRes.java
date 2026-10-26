package com.github.gaohongf.knowledge.entity.res;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识库详情 = 列表行 + Markdown 正文 */
@EqualsAndHashCode(callSuper = true)
@Data
public class KnowledgeArticleDetailRes extends KnowledgeArticleRes {

    private String content;
}
