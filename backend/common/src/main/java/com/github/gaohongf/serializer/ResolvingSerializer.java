package com.github.gaohongf.serializer;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * 把字段值（通常是个 id）替换成解析出来的对象再写出。
 * <p>
 * 由 {@link ResolveAnnotationIntrospector} 按字段上的注解构造, 与具体业务无关。
 */
@Slf4j
public class ResolvingSerializer extends JsonSerializer<Object> {

    /** 解析失败时只警告一次, 避免 auth 挂掉后日志被刷爆 */
    private static final AtomicBoolean DEGRADE_WARNED = new AtomicBoolean();

    private final ResolveStrategy<Object> strategy;
    private final Set<String> fields;

    @SuppressWarnings("unchecked")
    public ResolvingSerializer(ResolveStrategy<?> strategy, Annotation annotation) {
        this.strategy = (ResolveStrategy<Object>) strategy;
        this.fields = enabledFields(annotation);
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        if (value == null) {
            provider.defaultSerializeNull(generator);
            return;
        }

        Long id = toId(value);
        if (id == null) {
            // 注解标在了非数值字段上, 不做解析, 保持默认行为
            provider.defaultSerializeValue(value, generator);
            return;
        }

        Object rendered;
        try {
            Object resolved = strategy.resolve(id);
            // 投影和解析一起兜住: FieldProjector 走反射, 同样可能失败,
            // 放在 try 外面等于白兜异常。
            rendered = resolved == null ? null : strategy.project(resolved, fields);
        } catch (Exception failure) {
            // 这里绝对不能往外抛: 响应体已经开始写出, 抛出去只会得到一截非法 JSON,
            // 全局异常处理器没有机会介入。降级渲染, 保证客户端始终拿到合法 JSON。
            // 注意只兜 Exception —— Error 该让线程死掉, 吞掉 OOM 只会把问题藏得更深。
            warnDegraded(failure);
            rendered = null;
        }

        if (rendered == null) {
            // 降级形态: 至少把 id 还给客户端, 让它知道这个字段指向谁
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("id", id);
            provider.defaultSerializeValue(fallback, generator);
            return;
        }

        provider.defaultSerializeValue(rendered, generator);
    }

    private void warnDegraded(Exception failure) {
        if (DEGRADE_WARNED.compareAndSet(false, true)) {
            log.warn("字段解析失败, 已降级为只输出 id。后续同类失败不再重复记录。"
                    + "这通常意味着被解析对象所属的服务不可用或超时。", failure);
        } else {
            log.debug("字段解析失败, 降级为只输出 id", failure);
        }
    }

    /**
     * 取出注解里返回类型为 boolean 且值为 true 的成员名, 保持声明顺序。
     * <p>
     * 这样 {@code @User(username = true)} 这种声明不用写任何解析代码 ——
     * 将来 {@code @Dept(name = true)} 同样开箱可用。
     */
    static Set<String> enabledFields(Annotation annotation) {
        Set<String> fields = new LinkedHashSet<>();
        for (Method member : annotation.annotationType().getDeclaredMethods()) {
            if (member.getReturnType() != boolean.class) {
                continue;
            }
            try {
                if (Boolean.TRUE.equals(member.invoke(annotation))) {
                    fields.add(member.getName());
                }
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
                // 读不出来的成员当作没开启
            }
        }
        // 不能用 Set.copyOf: 它不保证迭代顺序, 会让输出字段顺序随 JVM 而变
        return Collections.unmodifiableSet(fields);
    }

    /**
     * 目前只认数值类型的 id。注解标在别的类型上时返回 null, 交由调用方按默认行为序列化。
     */
    private static Long toId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
