package com.github.gaohongf.ai.graph.wo.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.entity.dto.IntentOption;

import reactor.core.publisher.Flux;

/**
 * 故障确认问题下面的一组卡片：每篇命中文章一项（"是打印机卡纸了"），最后一项"都不是"。
 *
 * <p>
 * 卡片文字由 {@link KnowledgeProbeNode} 生成（用户口径的一句话），点下去时前端把它
 * 当成一条用户发言发回来 —— 所以这里不需要新的动作协议，走的就是普通的一轮输入。
 * 概率字段固定为 0：前端对这些卡不显示编号和百分比。
 */
public class ProbeOptionsNode extends WorkflowNode {

    /** 最后一张否认卡片：点了之后按"都不是"重新走一轮检索 */
    private static final String DENY_ANSWER = "都不是";

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        List<String> answers = state.value("probeAnswers", List.class)
                .map(items -> items.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList())
                .orElse(List.of());

        List<IntentOption> options = new ArrayList<>(answers.size() + 1);
        for (String answer : answers) {
            options.add(new IntentOption(options.size() + 1, answer, "你遇到的就是这个情况", 0, null));
        }
        options.add(new IntentOption(options.size() + 1, DENY_ANSWER, "不是上面任何一种情况", 0, null));
        return Map.of("options", Flux.fromIterable(options));
    }
}
