package com.github.gaohongf.knowledge.support;

import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.gaohongf.knowledge.entity.po.KnowledgeArticleEntity;

/**
 * 关键字检索的公共处理。
 *
 * <h2>为什么要手动转义</h2>
 * MyBatis-Plus 的 {@code like()} 不转义 {@code %} 和 {@code _}，直接拼 ILIKE 的话
 * 用户搜一个 "%" 就会命中全部文章。这里统一转义并显式声明 {@code ESCAPE '\'}。
 *
 * <h2>为什么用 ILIKE 而不是全文检索</h2>
 * 默认的 Postgres 分词配置对中文效果很差，本轮就是子串匹配；
 * 更聪明的语义匹配由向量检索承担。
 */
public final class KnowledgeKeywords {

    private static final String ILIKE_CLAUSE =
            "(title ILIKE {0} ESCAPE '\\' OR summary ILIKE {0} ESCAPE '\\'"
                    + " OR content ILIKE {0} ESCAPE '\\')";

    private KnowledgeKeywords() {
    }

    /** 空串和纯空白视为"没有关键字" */
    public static String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    /** 给查询加上标题/摘要/正文的子串匹配；keyword 为 null 时不做任何事 */
    public static void apply(LambdaQueryWrapper<KnowledgeArticleEntity> wrapper, String keyword) {
        if (keyword == null) {
            return;
        }
        String pattern = "%" + keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
        wrapper.apply(ILIKE_CLAUSE, pattern);
    }
}
