package com.github.gaohongf.serializer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * "可解析字段"序列化能力的自动装配。
 * <p>
 * 各服务只扫自己的包, 所以必须走 {@code AutoConfiguration.imports} 才能生效。
 */
@AutoConfiguration
@ConditionalOnClass(JsonSerializer.class)
public class ResolveAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ResolveStrategyRegistry resolveStrategyRegistry(ObjectProvider<ResolveStrategy<?>> strategies) {
        return new ResolveStrategyRegistry(strategies.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(name = "resolveJacksonCustomizer")
    public Jackson2ObjectMapperBuilderCustomizer resolveJacksonCustomizer(ResolveStrategyRegistry registry) {
        return new ResolveJacksonCustomizer(registry);
    }

    /**
     * 用 {@code AnnotationIntrospectorPair} 而不是直接 set, 是因为
     * {@code builder.annotationIntrospector(...)} 是<b>替换</b>语义 —— 直接塞自定义实现会把默认的
     * {@link JacksonAnnotationIntrospector} 干掉, 导致 {@code @JsonProperty} / {@code @JsonIgnore}
     * 等全线失效, 而且症状很隐蔽（只是字段名或过滤行为悄悄变了）。
     */
    static class ResolveJacksonCustomizer implements Jackson2ObjectMapperBuilderCustomizer, Ordered {

        private final ResolveStrategyRegistry registry;

        ResolveJacksonCustomizer(ResolveStrategyRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void customize(Jackson2ObjectMapperBuilder builder) {
            builder.annotationIntrospector(AnnotationIntrospector.pair(
                    new ResolveAnnotationIntrospector(registry),
                    new JacksonAnnotationIntrospector()));
        }

        @Override
        public int getOrder() {
            // 放到最后, 保证它设置 introspector 之后没有别的 customizer 把它覆盖掉
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
