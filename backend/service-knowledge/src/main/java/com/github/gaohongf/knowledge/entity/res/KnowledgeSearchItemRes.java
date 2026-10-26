package com.github.gaohongf.knowledge.entity.res;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 页面搜索结果行。
 *
 * <p>
 * {@code score} 只在语义检索命中时有值；关键字补充进来的行保持 null，
 * 前端显示 "—"，不把它当成 0 分。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class KnowledgeSearchItemRes extends KnowledgeArticleRes {

    private Double score;
}
