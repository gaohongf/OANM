package com.github.gaohongf.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页结果。
 *
 * <h2>为什么不直接返回 MyBatis-Plus 的 IPage</h2>
 * {@code Page} 序列化出来会带上 {@code orders} / {@code optimizeCountSql} /
 * {@code searchCount} / {@code countId} / {@code maxLimit} 这些调用方完全用不到的内部字段,
 * 等于把持久层的实现细节写进了接口契约 —— 将来换掉分页实现就会变成前端的破坏性变更。
 * <p>
 * 这里只暴露四个真正有用的字段。
 *
 * <h2>为什么放在 common 而不是某个服务的 entity.res 里</h2>
 * 它原本在 {@code service-auth.entity.res} 下, 第一个分页接口用的就是它。出现第二个需要分页的
 * 服务（service-work-order）时, 复制一份的代价不是那几十行代码, 而是
 * {@link #MAX_SIZE} —— 那是一条<b>策略</b>（防止调用方传 {@code size=100000} 把整张表拉进内存）,
 * 散在两个模块里迟早会不一致, 而"哪个才是真的上限"从代码上看不出来。
 * 放在 {@link BaseEntity} 旁边, 是因为两者是同一类东西: 跨服务的通用数据形状。
 *
 * @param <T> 记录类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageRes<T> {

    /**
     * 单页条数上限。
     * <p>
     * 放在这里而不是各个 Service 里各写一份：这是<b>一条策略</b>，散在多处迟早会不一致。
     */
    public static final long MAX_SIZE = 200;

    private static final long DEFAULT_SIZE = 10;

    /** 总记录数 */
    private long total;

    /** 当前页码, 从 1 开始 */
    private long current;

    /** 每页条数 */
    private long size;

    /** 当前页数据 */
    private List<T> records;

    /** 把调用方传来的页码收敛到合法范围（小于 1 视为第 1 页） */
    public static long clampCurrent(long current) {
        return current < 1 ? 1 : current;
    }

    /** 把调用方传来的每页条数收敛到合法范围（小于 1 用默认值，超过上限取上限） */
    public static long clampSize(long size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
