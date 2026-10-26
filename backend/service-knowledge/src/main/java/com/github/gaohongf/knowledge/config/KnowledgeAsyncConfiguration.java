package com.github.gaohongf.knowledge.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 索引线程池。
 *
 * <h2>为什么是 2 个线程</h2>
 * 嵌入调用受本机 Ollama 的显存限制，并发再高只会让每个请求都变慢。
 * 队列 100 足够吸收一次批量发布。
 *
 * <h2>为什么用 AbortPolicy</h2>
 * 队列满时明确拒绝，由监听器把文章标成 FAILED 并写明原因。
 * 换成 CallerRunsPolicy 会让保存请求线程去跑几秒钟的嵌入 ——
 * 那正是异步索引要避免的事。
 */
@Configuration
public class KnowledgeAsyncConfiguration {

    public static final String INDEX_EXECUTOR = "knowledgeIndexExecutor";

    @Bean(INDEX_EXECUTOR)
    public ThreadPoolTaskExecutor knowledgeIndexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("knowledge-index-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
