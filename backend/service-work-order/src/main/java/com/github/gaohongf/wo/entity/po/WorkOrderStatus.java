package com.github.gaohongf.wo.entity.po;

import java.util.Optional;

/**
 * 工单状态。
 *
 * <p>
 * 普通工单建单时一律是 {@link #NEW}；自助解决记录建单时直接是 {@link #RESOLVED}。
 * 两者都由服务端按 {@code self_resolved} 标记决定，<b>不由客户端指定状态</b> ——
 * 建单接口的入参里没有状态字段。
 *
 * <p>
 * 接取/派发把状态写成 {@link #ACCEPTED}，受理人结单写成 {@link #RESOLVED}，
 * 取消（没人受理时直接取消，或受理人同意取消申请）写成 {@link #CLOSED}。
 * 已受理但等待受理人确认的取消申请不改变状态，记录在 {@code cancel_requested_at} 上。
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
