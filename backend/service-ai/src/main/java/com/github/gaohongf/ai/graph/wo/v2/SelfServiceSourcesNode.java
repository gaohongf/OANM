package com.github.gaohongf.ai.graph.wo.v2;

import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.entity.dto.KnowledgeSource;

import reactor.core.publisher.Flux;

/**
 * 把自助建议引用的文章作为单独一帧发给前端：一条一帧，带文章 id。
 *
 * <p>
 * 不再拼成"参考知识：《…》"塞在步骤末尾 —— 那样只有标题，点不开原文。
 * 这一帧先于引导语和步骤发出，前端一边渲染一边就知道出处能跳到哪。
 */
public class SelfServiceSourcesNode extends WorkflowNode {

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        List<?> items = state.value("selfServiceSources", List.class).orElse(List.of());
        List<KnowledgeSource> sources = items.stream()
                .filter(KnowledgeSource.class::isInstance)
                .map(KnowledgeSource.class::cast)
                .toList();
        if (sources.isEmpty()) {
            return Map.of();
        }
        // 用独立的状态键：selfServiceSources 要原样留着，不能被流替换掉
        return Map.of("sourceItems", Flux.fromIterable(sources));
    }
}
