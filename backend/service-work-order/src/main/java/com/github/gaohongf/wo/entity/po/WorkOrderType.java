package com.github.gaohongf.wo.entity.po;

import java.util.Optional;

/**
 * 工单类型: 需求还是故障。
 *
 * <h2>为什么要有这个区分</h2>
 * AI 的意图推断本来就在做这件事（提示词第一条就是"区分用户输入是需求还是故障"），
 * 把这个判断固化成一列，之后才能按类型分流（需求走排期、故障走抢修），
 * 否则这个判断只活在模型的上下文里，出了这一次对话就没了。
 *
 * <h2>存的是 {@code name()} 而不是序号</h2>
 * MyBatis 的 {@code EnumTypeHandler} 把枚举存成名字。没用序号是因为序号会随
 * 枚举里加一行就整体错位 —— 那种错误不会报错，只会让历史数据的意思悄悄改变。
 */
public enum WorkOrderType {

    /** 需求 */
    DEMAND,

    /** 故障 */
    FAULT;

    /**
     * 宽松解析。
     *
     * <h2>为什么不由 Jackson 直接把请求体绑成枚举</h2>
     * 那样非法值会变成"报文解析失败"，调用方拿到的是一个和业务无关的框架错误。
     * 收 {@code String} 再在这里解析，就能给出"工单类型只能是 demand 或 fault"这种
     * 能被直接理解的提示（由调用方 {@code R.error(...)}）。
     *
     * <h2>为什么要容错中文</h2>
     * 提示词已经收紧成 {@code demand|fault}，前端也会归一化，这里是第三层。
     * 留着它是因为模型的输出是最不可控的一环：给它固定枚举，它仍有概率吐出"故障"这种
     * 等价但字面不同的值。这种情况按同义词收下比直接拒绝更合理 —— 语义是明确的，
     * 没有猜测成分。刻意只列<b>明确的同义词</b>，不做模糊匹配：拿不准的值应当报错。
     */
    public static Optional<WorkOrderType> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        for (WorkOrderType value : values()) {
            if (value.name().equalsIgnoreCase(text)) {
                return Optional.of(value);
            }
        }
        return switch (text) {
            case "需求", "新需求", "功能需求", "demand" -> Optional.of(DEMAND);
            case "故障", "问题", "报错", "异常", "fault" -> Optional.of(FAULT);
            default -> Optional.empty();
        };
    }
}
