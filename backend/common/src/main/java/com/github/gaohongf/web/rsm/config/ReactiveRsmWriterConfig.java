package com.github.gaohongf.web.rsm.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import com.github.gaohongf.web.rsm.ReactiveRsmWriter;
import com.lingyun.base.response.ResponseBuilder;
import com.lingyun.base.rsm.message.Response;

/**
 * 反应式服务的 {@link ReactiveRsmWriter} 装配。
 *
 * <h2>为什么要写进 {@code AutoConfiguration.imports}</h2>
 * common 是纯类库, 没有任何包被组件扫描到（各服务的启动类都在自己的包下）, 所以这里的
 * {@code @Bean} 只有列进 imports 文件才存在。与 {@code RsmConfig} 同一条。
 *
 * <h2>为什么必须限定 reactive</h2>
 * common 也在 servlet 服务的 classpath 上, 而那边的信封由 RSM 的 servlet 适配器自动包装,
 * 谁都不会去注入这个 writer。限定类型只是让"这个 bean 在 servlet 服务里干脆不存在"显式化 ——
 * 万一有人真在 servlet 控制器里拿到它, {@code ServerWebExchange} 那关就过不去,
 * 不会安静地少包一层。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveRsmWriterConfig {

    /**
     * 注入 {@code ResponseBuilder<Response>} 时会解析到 {@code ReactiveMessageResponseBuilder}
     * （反应式服务在自己的启动类上 {@code @Import} 它, 且它标了 {@code @Primary}）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ReactiveRsmWriter reactiveRsmWriter(ResponseBuilder<Response> responseBuilder) {
        return new ReactiveRsmWriter(responseBuilder);
    }
}
