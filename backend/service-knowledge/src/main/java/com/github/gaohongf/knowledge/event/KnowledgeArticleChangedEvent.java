package com.github.gaohongf.knowledge.event;

/**
 * 文章需要（重新）建索引。
 *
 * <p>
 * 在保存/发布所在的短事务里发布，监听方用
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 接住 —— 索引线程必须读到已提交的数据。
 */
public record KnowledgeArticleChangedEvent(Long articleId) {
}
