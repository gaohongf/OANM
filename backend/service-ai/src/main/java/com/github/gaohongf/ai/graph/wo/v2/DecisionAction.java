package com.github.gaohongf.ai.graph.wo.v2;

import java.util.Optional;

/** 用户在自助解决建议之后可以选的两个动作。 */
public enum DecisionAction {

    /** 问题已经解决，生成一条自助解决记录 */
    SELF_RESOLVED,

    /** 没解决，继续走工单流程 */
    WORK_ORDER;

    public static Optional<DecisionAction> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        for (DecisionAction value : values()) {
            if (value.name().equalsIgnoreCase(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** 本轮是否有明确的决策动作；没有时返回 null */
    public static DecisionAction fromState(com.alibaba.cloud.ai.graph.OverAllState state) {
        return parse(state.value("decisionAction", String.class).orElse(null)).orElse(null);
    }
}
