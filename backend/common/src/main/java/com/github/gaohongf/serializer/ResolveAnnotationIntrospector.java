package com.github.gaohongf.serializer;

import java.lang.annotation.Annotation;

import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;

/**
 * 让 Jackson 认识"可解析字段"注解（如 {@link com.github.gaohongf.serializer.annotation.User}）。
 * <p>
 * 只覆写 {@code findSerializer}, 只影响<b>序列化</b>: 入参 JSON 里这个字段仍然应该是裸 id。
 * 这个不对称是有意设计的, 不要顺手补 {@code findDeserializer}。
 * <p>
 * 它会被注册成 {@code AnnotationIntrospectorPair} 的 primary, 未命中时返回 null,
 * Jackson 会自动落到 secondary（即默认的 {@code JacksonAnnotationIntrospector}）。
 * 因此 {@code @JsonProperty} / {@code @JsonIgnore} / {@code @JsonFormat} 等行为不受影响。
 */
public class ResolveAnnotationIntrospector extends NopAnnotationIntrospector {

    private static final long serialVersionUID = 1L;

    private final transient ResolveStrategyRegistry registry;

    public ResolveAnnotationIntrospector(ResolveStrategyRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Object findSerializer(Annotated annotated) {
        for (ResolveStrategy<?> strategy : registry.all()) {
            Annotation annotation = annotated.getAnnotation(strategy.annotation());
            if (annotation != null) {
                return new ResolvingSerializer(strategy, annotation);
            }
        }
        return null;
    }
}
