package com.github.gaohongf.ai.graph.wo.v2;

import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.entity.dto.IntentOption;

import reactor.core.publisher.Flux;

/**
 * 自助建议之后的两张决策卡片。
 *
 * <p>
 * 概率字段固定为 0：这两项不是"猜哪个更像"，不应显示百分比。
 * 前端看到 {@code action} 非空就不再渲染编号、概率和进度条。
 */
public class DecisionOptionsNode extends WorkflowNode {

    private static final IntentOption SELF_RESOLVED = new IntentOption(
            1,
            "已经解决了",
            "按上面的方法处理后，问题已经解决。",
            0,
            DecisionAction.SELF_RESOLVED.name());

    private static final IntentOption WORK_ORDER = new IntentOption(
            2,
            "还没解决，帮我提工单",
            "尝试后仍未解决，需要人工处理。",
            0,
            DecisionAction.WORK_ORDER.name());

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        return Map.of(
                "options", Flux.just(SELF_RESOLVED, WORK_ORDER),
                "phase", WorkflowPhase.DECISION);
    }

    /** 给测试和文档用；实际输出走上面的 Flux。 */
    public static List<IntentOption> options() {
        return List.of(SELF_RESOLVED, WORK_ORDER);
    }
}
