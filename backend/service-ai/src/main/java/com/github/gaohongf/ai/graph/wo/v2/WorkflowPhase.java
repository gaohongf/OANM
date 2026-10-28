package com.github.gaohongf.ai.graph.wo.v2;

import com.alibaba.cloud.ai.graph.OverAllState;

/**
 * 会话所处的阶段。
 *
 * <p>
 * 它不是给用户看的状态，而是给路由用的：同样是"用户又说了一句话"，
 * 在收集信息阶段要重新提取并判断能否检索知识库，在选项阶段要重新给工单意图，
 * 在决策阶段要先判断用户是不是已经解决了。没有这个标记，三种情况会走同一条边。
 */
public final class WorkflowPhase {

    /** 还在收集时间/地点等关键信息 */
    public static final String COLLECTING = "COLLECTING";

    /** 已经给出工单意图选项，等用户挑一个 */
    public static final String OPTIONS = "OPTIONS";

    /** 已经给出自助解决建议，等用户回答是否解决 */
    public static final String DECISION = "DECISION";

    /**
     * 已经确定要人工处理（用户要提工单，或自助方案不可用）。
     *
     * <p>
     * 这个阶段不再推自助方案；时间、地点也只在进入这里之后才需要 ——
     * 自助解决并不依赖它们，先问这两样会把现成的解决办法挡在后面。
     */
    public static final String WORK_ORDER = "WORK_ORDER";

    private WorkflowPhase() {
    }

    public static String of(OverAllState state) {
        return state.value("phase", String.class).orElse(COLLECTING);
    }
}
