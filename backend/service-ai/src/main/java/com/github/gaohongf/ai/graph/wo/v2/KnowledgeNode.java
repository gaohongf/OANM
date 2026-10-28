package com.github.gaohongf.ai.graph.wo.v2;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.client.KnowledgeClient;
import com.github.gaohongf.ai.config.KnowledgeProperties;
import com.github.gaohongf.ai.entity.dto.KnowledgeHit;
import com.github.gaohongf.ai.entity.dto.WorkOrderProblemDraft;

import lombok.extern.slf4j.Slf4j;

/**
 * 用用户的问题去知识库检索可用片段。
 *
 * <p>
 * 不调用大模型。检索不到、分数太低或检索服务不可用，都只写一个空列表：
 * 启动器的条件边据此跳过自助解决，直接进入工单意图。
 */
@Slf4j
public class KnowledgeNode extends WorkflowNode {

    private static final int MAX_QUERY_LENGTH = 1000;

    private final KnowledgeClient knowledgeClient;
    private final KnowledgeProperties properties;

    public KnowledgeNode(KnowledgeClient knowledgeClient, KnowledgeProperties properties) {
        this.knowledgeClient = knowledgeClient;
        this.properties = properties;
    }

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        // 选项阶段只是在微调工单意图，工单阶段已经决定要人工了，都不会再回来自助解决；
        // 不查，省一次嵌入调用（知识库不可用时这一次调用还要等满超时）
        String phase = WorkflowPhase.of(state);
        if (!properties.isEnabled()
                || WorkflowPhase.OPTIONS.equals(phase)
                || WorkflowPhase.WORK_ORDER.equals(phase)) {
            return Map.of("knowledgeHits", List.of());
        }
        String query = buildQuery(state);
        if (!StringUtils.hasText(query)) {
            return Map.of("knowledgeHits", List.of());
        }

        List<KnowledgeHit> hits = knowledgeClient.retrieve(
                query,
                properties.getTopK(),
                Duration.ofSeconds(properties.getTimeoutSeconds()));

        List<KnowledgeHit> usable = hits.stream()
                .filter(hit -> hit.getScore() >= properties.getMinScore())
                .filter(hit -> StringUtils.hasText(hit.getContent()))
                .limit(properties.getTopK())
                .toList();

        if (usable.isEmpty()) {
            log.debug("知识库没有可用片段，跳过自助解决: query={}", query);
        }
        // 可变列表：它会被写进工作流状态并随 checkpoint 落 Redis
        return Map.of("knowledgeHits", new ArrayList<>(usable));
    }

    /**
     * 用"问题 + 地点"做查询。
     *
     * <p>
     * 不拼时间：同一类故障的文档不会因为发生在昨天还是上周而不同，时间只会稀释语义。
     * 地点则可能决定用哪套设备/哪栋楼的文档。
     */
    private static String buildQuery(OverAllState state) {
        WorkOrderProblemDraft draft = state.value("draft", WorkOrderProblemDraft.class).orElse(null);
        String problem = draft == null ? null : draft.getUserMessages();
        if (!StringUtils.hasText(problem)) {
            problem = state.value("userInput", String.class).orElse("");
        }

        StringBuilder query = new StringBuilder();
        if (StringUtils.hasText(problem)) {
            query.append(problem.trim());
        }
        if (draft != null && StringUtils.hasText(draft.getUserLocationDescription())) {
            String location = draft.getUserLocationDescription().trim();
            if (!"未知".equals(location)) {
                if (!query.isEmpty()) {
                    query.append("；");
                }
                query.append("地点：").append(location);
            }
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            query.setLength(MAX_QUERY_LENGTH);
        }
        return query.toString();
    }
}
