package com.github.gaohongf.serializer;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按字段名集合把一个对象投影成 Map, 供序列化器写出。
 * <p>
 * 结果用 {@link LinkedHashMap}, 遍历顺序即传入的 {@code fields} 顺序。
 * 读不到的字段直接跳过 —— 序列化期间绝不能因为一个 getter 就炸掉整个响应。
 * <p>
 * 反射结果按类缓存, 类型首次出现时才做一次内省。
 */
public final class FieldProjector {

    private static final Map<Class<?>, Map<String, Method>> ACCESSOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> NO_ACCESSORS = Map.of();

    private FieldProjector() {
    }

    public static Map<String, Object> project(Object value, Set<String> fields) {
        Map<String, Object> projected = new LinkedHashMap<>();
        if (value == null || fields == null || fields.isEmpty()) {
            return projected;
        }
        Map<String, Method> accessors = ACCESSOR_CACHE.computeIfAbsent(value.getClass(), FieldProjector::introspect);
        for (String field : fields) {
            Method accessor = accessors.get(field);
            if (accessor == null) {
                continue;
            }
            try {
                projected.put(field, accessor.invoke(value));
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
                // 单个字段读不出来就跳过, 不影响其它字段
            }
        }
        return projected;
    }

    private static Map<String, Method> introspect(Class<?> type) {
        Map<String, Method> accessors = new HashMap<>();
        try {
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors()) {
                Method readMethod = descriptor.getReadMethod();
                if (readMethod != null) {
                    accessors.put(descriptor.getName(), makeAccessible(readMethod));
                }
            }
        } catch (IntrospectionException | RuntimeException ignored) {
            // 内省失败就退化成下面的"按字段名找同名方法"
        }
        // 兼容 record 风格的访问器: userRes.username() 这种没有 get 前缀的同名方法
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                accessors.putIfAbsent(method.getName(), makeAccessible(method));
            }
        }
        return accessors.isEmpty() ? NO_ACCESSORS : Map.copyOf(accessors);
    }

    private static Method makeAccessible(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            method.trySetAccessible();
        }
        return method;
    }
}
