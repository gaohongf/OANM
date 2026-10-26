package com.github.gaohongf.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.gaohongf.knowledge.rsm.KnowledgeRsm;

/**
 * 注册本服务的 RSM 消息类。
 *
 * <p>
 * 不给 {@link KnowledgeRsm} 加 {@code @Component}：消息类以后可能被挪进 common，
 * 那时它的包不再被任何服务的扫描覆盖，注册会静默失效。
 */
@Configuration
public class KnowledgeRsmConfiguration {

    @Bean
    public KnowledgeRsm knowledgeRsm() {
        return new KnowledgeRsm();
    }
}
