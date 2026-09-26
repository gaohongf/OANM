package com.github.gaohongf.serializer;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一种"字段值 → 对象"的解析策略。
 * <p>
 * 职责边界: 本接口只负责<b>取数</b>（给一批 id, 还一个 id → 对象 的 map）, 不负责
 * 渲染。渲染由 {@link ResolvingSerializer} 统一处理, 输出哪些字段由注解声明。
 * 这样新增一种解析目标（部门、角色……）只需要"加一个注解 + 加一个实现", 框架零改动。
 *
 * @param <T> 解析出来的对象类型
 */
public interface ResolveStrategy<T> {

    /**
     * 本策略负责哪个注解。注册表按注解类型建索引, 一个注解只能对应一个策略。
     */
    Class<? extends Annotation> annotation();

    /**
     * 批量解析。这是唯一的取数原语 —— 单条解析由 {@link #resolve} 代为实现。
     * <p>
     * 之所以一开始就是批量签名: 序列化时一个响应里往往有多个字段指向同一批 id,
     * 将来换成真正的批量远程调用时, 调用方和接口都不需要改动。
     * <p>
     * 实现约定:
     * <ul>
     *   <li>查不到的 id <b>不要</b>放进 map（不要放 null 值）, 调用方按"缺失"处理</li>
     *   <li>远程调用失败时不要抛异常, 返回已有的部分结果即可 —— 见 {@link #resolve} 的说明</li>
     *   <li>既然异常被吞掉, <b>实现方就必须自己记日志</b>。吞掉之后"取数失败"和
     *       "这个 id 本来就不存在"对上层是完全一样的, 而后者是正常业务、
     *       前者是故障。不记日志的话故障会彻底静默。参考
     *       {@code UserResolveStrategy} 的"每进程只 WARN 一次"写法。</li>
     * </ul>
     */
    Map<Long, T> resolveBatch(Collection<Long> ids);

    /**
     * 单条解析。
     * <p>
     * <b>实现方注意</b>: 这个方法会在 JSON 序列化过程中被调用。此时响应体已经开始写出,
     * 任何异常都会导致吐出一截非法 JSON 且无法被全局异常处理器兜住。所以实现必须
     * 自己吞掉远程调用异常并返回 null, 由调用方降级渲染。
     */
    default T resolve(Long id) {
        if (id == null) {
            return null;
        }
        return resolveBatch(List.of(id)).get(id);
    }

    /**
     * 按注解里为 true 的成员名, 把解析结果投影成待序列化的结构。
     * <p>
     * 默认实现走反射读 getter（见 {@link FieldProjector}）; 需要特殊渲染时覆写。
     *
     * @param fields 注解里值为 true 的成员名, 保持声明顺序
     */
    default Object project(T value, Set<String> fields) {
        return FieldProjector.project(value, fields);
    }
}
