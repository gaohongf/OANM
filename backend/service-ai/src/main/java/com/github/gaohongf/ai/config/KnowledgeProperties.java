package com.github.gaohongf.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/** 自助解决相关的可调参数。 */
@Data
@Component
@ConfigurationProperties(prefix = "oanm.ai.knowledge")
public class KnowledgeProperties {

    /** 关掉后直接跳过自助解决，所有问题都走工单流程 */
    private boolean enabled = true;

    /** 一次检索取回多少个片段 */
    private int topK = 5;

    /**
     * 低于该相似度的片段不参与自助解决。
     * 知识库内部检索本身不做分数过滤，这里必须自己挡一道 —— 否则不相关的片段
     * 也会被模型"硬凑"成答案。
     */
    private double minScore = 0.25;

    /**
     * 拿去"确认故障"的最低相似度。
     *
     * <p>
     * 比 {@link #minScore} 高：这一步是主动问用户"是哪种故障"，猜错比不问更烦人。
     * 低于它的片段仍然可以参与自助解决判断，只是不会变成一句提问。
     */
    private double probeMinScore = 0.35;

    /**
     * 检索超时。知识库不可用时尽快跳过，别让整段对话卡住。
     *
     * <p>
     * 不能太短：嵌入模型冷启动（Ollama 空闲卸载后重新加载）要几秒到十几秒，
     * 8 秒会把这种正常情况误判成服务不可用，直接丢掉这一轮的知识。
     */
    private int timeoutSeconds = 20;
}
