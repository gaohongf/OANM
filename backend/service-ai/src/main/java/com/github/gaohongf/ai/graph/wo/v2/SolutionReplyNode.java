package com.github.gaohongf.ai.graph.wo.v2;

import java.util.Map;

import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.graph.OverAllState;

import reactor.core.publisher.Flux;

/** 把已经生成好的自助解决步骤作为这一轮的 AI 回答发给前端。 */
public class SolutionReplyNode extends WorkflowNode {

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        String solution = state.value("selfServiceSolution", String.class).orElse("");
        if (!StringUtils.hasText(solution)) {
            return Map.of();
        }
        return Map.of("messages", Flux.just(solution));
    }
}
