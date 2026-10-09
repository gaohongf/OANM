package com.github.gaohongf.wo.entity.po;

import java.util.Optional;

/**
 * 工单优先级。
 *
 * <h2>为什么是四档而不是"高/中/低"三档</h2>
 * "紧急"这一档的单子必须能插队到"高"前面 —— 有紧急档时 "高" 才敢用来表示
 * "重要但不打断当前工作"。三档的话所有急事都会挤到"高"，那一档就失去了区分度。
 *
 * <h2>为什么不用数值（1-5）</h2>
 * 数值需要一张对照表才知道 3 是什么意思，而且排序方向（1 最高还是 5 最高）是最经典的
 * 记反点。四档的取值本身就是自解释的。
 */
public enum WorkOrderPriority {

    /** 低 */
    LOW,

    /** 中 */
    MEDIUM,

    /** 高 */
    HIGH,

    /** 紧急 */
    URGENT;

    /**
     * 宽松解析, 与 {@link WorkOrderType#parse(String)} 同一套理由。
     * <p>
     * 这里的中文同义词尤其重要: 提示词原先只写了"工单优先级"而没有给枚举, 模型很可能
     * 吐出"高"/"紧急"这类词。历史对话里已经产出过的这种值不该在收紧提示词后变成废数据。
     */
    public static Optional<WorkOrderPriority> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        for (WorkOrderPriority value : values()) {
            if (value.name().equalsIgnoreCase(text)) {
                return Optional.of(value);
            }
        }
        return switch (text) {
            case "低", "低优先级" -> Optional.of(LOW);
            case "中", "一般", "普通", "中优先级" -> Optional.of(MEDIUM);
            case "高", "高优先级", "重要" -> Optional.of(HIGH);
            case "紧急", "特急", "严重", "最高", "紧急优先级" -> Optional.of(URGENT);
            default -> Optional.empty();
        };
    }
}
