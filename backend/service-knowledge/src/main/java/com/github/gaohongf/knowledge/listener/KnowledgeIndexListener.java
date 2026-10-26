package com.github.gaohongf.knowledge.listener;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.github.gaohongf.knowledge.event.KnowledgeArticleChangedEvent;
import com.github.gaohongf.knowledge.service.KnowledgeIndexService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 事务提交后把文章丢进索引线程池。
 *
 * <p>
 * {@code fallbackExecution = true}：没有活动事务时也执行（例如启动恢复或手工重建），
 * 否则事件会被静默丢弃。
 *
 * <p>
 * 跨 bean 调用 {@link KnowledgeIndexService#rebuildAsync(Long)}，{@code @Async} 才会生效；
 * 在同一 bean 内自调用会绕过代理，变成同步执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexListener {

    private static final String QUEUE_REJECTED = "索引队列已满，请稍后点击重建索引";

    private final KnowledgeIndexService indexService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChanged(KnowledgeArticleChangedEvent event) {
        try {
            indexService.rebuildAsync(event.articleId());
        } catch (TaskRejectedException rejected) {
            // 不能让异常冒到保存请求：文章已经保存成功，索引可以稍后重做
            log.warn("知识库索引队列已满，文章 {} 本次重建被拒绝", event.articleId());
            indexService.markQueueRejected(event.articleId(), QUEUE_REJECTED);
        }
    }
}
