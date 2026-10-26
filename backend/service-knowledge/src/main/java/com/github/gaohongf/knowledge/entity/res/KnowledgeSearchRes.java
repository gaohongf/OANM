package com.github.gaohongf.knowledge.entity.res;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 页面搜索结果。
 *
 * <p>
 * {@code mode} 告诉前端这次是语义检索还是降级后的关键字检索：
 * {@code SEMANTIC} / {@code KEYWORD}。降级是正常路径，不是错误。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeSearchRes {

    private String mode;

    private List<KnowledgeSearchItemRes> items;
}
