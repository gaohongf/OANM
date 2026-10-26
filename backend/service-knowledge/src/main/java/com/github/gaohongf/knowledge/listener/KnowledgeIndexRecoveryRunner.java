package com.github.gaohongf.knowledge.listener;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.github.gaohongf.knowledge.repository.KnowledgeIndexRepository;
import com.github.gaohongf.knowledge.service.KnowledgeIndexService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动时把遗留的 INDEXING 重新入队。
 *
 * <p>
 * 进程被强杀会留下"已发布 + 索引中"的中间态，没有任何线程会再处理它 ——
 * 不恢复的话这篇文章会永远显示"索引中"。
 *
 * <p>
 * FAILED 刻意不自动重试：Ollama 不可用时重启会造成重试风暴，
 * 交给管理页的「重建索引」由人决定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexRecoveryRunner implements ApplicationRunner {

    private final KnowledgeIndexRepository indexRepository;
    private final KnowledgeIndexService indexService;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> articleIds = indexRepository.findIndexingArticleIds();
        if (articleIds.isEmpty()) {
            return;
        }
        log.info("发现 {} 篇停留在索引中的知识库文章，重新入队", articleIds.size());
        articleIds.forEach(indexService::rebuildAsync);
    }
}
