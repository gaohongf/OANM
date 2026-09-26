package com.github.gaohongf.serializer;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注解类型 → 解析策略 的索引。
 * <p>
 * 由 {@link ResolveAutoConfiguration} 注入容器里所有的 {@link ResolveStrategy} 构建。
 * 同一个注解被两个策略声明时直接抛异常 —— 这种冲突晚发现不如早发现。
 * <p>
 * 内部用 {@link LinkedHashMap} 保序: {@code findSerializer} 会按顺序逐个试注解,
 * 顺序不稳定会让"哪个策略被选中"随 JVM 而变, 排查起来很痛苦。
 * {@code Map.copyOf} 会丢掉顺序, 所以这里用 {@code Collections.unmodifiableMap}。
 */
public class ResolveStrategyRegistry {

    private final Map<Class<? extends Annotation>, ResolveStrategy<?>> byAnnotation;
    /** 与 byAnnotation 同序, 但省掉每次查表时的视图分配 —— 它在每个字段的序列化里都会被遍历 */
    private final List<ResolveStrategy<?>> ordered;

    public ResolveStrategyRegistry(List<ResolveStrategy<?>> strategies) {
        Map<Class<? extends Annotation>, ResolveStrategy<?>> map = new LinkedHashMap<>();
        for (ResolveStrategy<?> strategy : strategies) {
            ResolveStrategy<?> previous = map.putIfAbsent(strategy.annotation(), strategy);
            if (previous != null) {
                throw new IllegalStateException("注解 " + strategy.annotation().getName()
                        + " 被多个 ResolveStrategy 声明: "
                        + previous.getClass().getName() + " 与 " + strategy.getClass().getName());
            }
        }
        this.byAnnotation = Collections.unmodifiableMap(map);
        this.ordered = List.copyOf(map.values());
    }

    public List<ResolveStrategy<?>> all() {
        return ordered;
    }

    public ResolveStrategy<?> findByAnnotation(Class<? extends Annotation> annotationType) {
        return byAnnotation.get(annotationType);
    }
}
