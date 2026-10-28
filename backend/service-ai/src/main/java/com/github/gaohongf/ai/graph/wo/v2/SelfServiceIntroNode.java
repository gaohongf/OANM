package com.github.gaohongf.ai.graph.wo.v2;

import java.util.Map;

import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.graph.OverAllState;

import reactor.core.publisher.Flux;

/**
 * 自助步骤之前的一句引导，如"可以按下面的步骤看看能不能解决？"。
 *
 * <p>
 * 单独一个节点而不是并进 {@link SolutionReplyNode}：前端要把这一句排成一段普通的话、
 * 把步骤排成编号列表 —— 同一帧里混着两种内容就分不开了。
 * 它先于步骤发出，用户先看到"可以试试这个"，再看到具体几步。
 */
public class SelfServiceIntroNode extends WorkflowNode {

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        String intro = state.value("selfServiceIntro", String.class).orElse("");
        if (!StringUtils.hasText(intro)) {
            return Map.of();
        }
        return Map.of("messages", Flux.just(intro));
    }
}
