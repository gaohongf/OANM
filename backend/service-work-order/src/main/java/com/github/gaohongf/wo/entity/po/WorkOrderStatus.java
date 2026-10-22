package com.github.gaohongf.wo.entity.po;

import java.util.Optional;

/**
 * 工单状态。
 *
 * <p>
 * 新建的工单一律是 {@link #NEW}，并且<b>不由客户端指定</b> —— 建单接口的入参里没有这个字段。
 * 允许调用方在建单时自报"已解决"是没有任何意义的，只是给状态机开了一个后门。
 *
 * <p>
 * 目前接取/派发会把状态写成 {@link #ACCEPTED}；{@link #RESOLVED} / {@link #CLOSED}
 * 还在等"解决/关闭"的接口。它们先留着，是因为它们定义了这张表的意图，
 * 而 {@code assignee_id} 那两列的存在意义就是服务于这条流水线。
 */
public enum WorkOrderStatus {

    /** 新创建 */
    NEW,

    /** 已受理 */
    ACCEPTED,

    /** 已解决 */
    RESOLVED,

    /** 已关闭 */
    CLOSED;

    /**
     * 宽松解析状态名，大小写不敏感。
     * <p>
     * 与 {@link WorkOrderType#parse} / {@link WorkOrderPriority#parse} 的区别是<b>没有中文别名</b>：
     * 那两个的值可能来自模型的自由输出（提示词收紧前更明显），这个只来自前端下拉框，
     * 不存在"模型写成中文"的情形。为不存在的调用方准备别名，只会让真正的输入错误更难被发现。
     *
     * @return 解析不出时返回 {@link Optional#empty()}，由调用方决定是拒绝还是当作"不过滤"
     */
    public static Optional<WorkOrderStatus> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        for (WorkOrderStatus value : values()) {
            if (value.name().equalsIgnoreCase(text)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
