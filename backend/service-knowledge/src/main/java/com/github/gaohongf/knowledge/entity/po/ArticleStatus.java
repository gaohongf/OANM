package com.github.gaohongf.knowledge.entity.po;

import java.util.Locale;
import java.util.Optional;

/**
 * 文章的业务状态。
 *
 * <p>
 * 只决定"是否对普通用户可见"，与索引状态无关：
 * 发布后索引失败的文章仍然能被关键字搜到，管理页会提示重建。
 */
public enum ArticleStatus {

    /** 草稿：仅管理端可见 */
    DRAFT,

    /** 已发布：知识库页可见，参与语义检索 */
    PUBLISHED;

    /** 宽松解析：去空格、忽略大小写。非法值返回 empty，由调用方映射成业务错误 */
    public static Optional<ArticleStatus> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        for (ArticleStatus value : values()) {
            if (value.name().equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
