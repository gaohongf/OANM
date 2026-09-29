package com.github.gaohongf.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.github.gaohongf.auth.interceptor.AuthorizationInterceptor;
import com.github.gaohongf.auth.rsm.AuthRsm;

/**
 * 把 {@link AuthorizationInterceptor} 装到所有业务服务上。
 *
 * <h2>为什么必须走自动装配</h2>
 * 原来这里是个普通 {@code @Configuration}, 而它所在的包 {@code com.github.gaohongf.auth.config}
 * 只有在 service-auth 里才被组件扫描到（各服务的启动类在 {@code com.github.gaohongf.<服务名>} 下）。
 * 结果就是<b>鉴权只覆盖了 service-auth 自己</b> —— service-work-order 无 token 可以直接访问。
 * <p>
 * 这与 {@code CacheConfiguration} 曾经踩的是同一个坑（见那个类的 Javadoc）: 写在 common 里的
 * 横切配置, 不走 {@code AutoConfiguration.imports} 就是死代码。
 *
 * <h2>两个条件</h2>
 * <ul>
 *   <li>{@code @ConditionalOnWebApplication(SERVLET)} —— common 也被 service-ai 依赖, 而它是
 *       WebFlux。往反应式应用里注册 {@code HandlerInterceptor} 会直接启动失败。</li>
 *   <li>{@code oanm.auth.enabled} —— 默认开启。留这个开关是因为打开后所有未登记权限的接口
 *       会立刻开始 403, 联调/排障时需要一个不改代码就能关掉的出口。</li>
 * </ul>
 *
 * <h2>打开它会立刻发生什么</h2>
 * 所有没有 {@code @IsOpen}、又没有对应权限数据的接口开始返回 403。这不是 bug, 是设计意图 ——
 * 但上线前必须先有权限数据（阶段二的管理端接口负责这件事）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "oanm.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthorizationAutoConfiguration implements WebMvcConfigurer {

    /**
     * 注册认证相关的消息键。
     * <p>
     * 必须在这里注册而不是把 {@link AuthRsm} 标成 {@code @Component} —— 见 {@link AuthRsm} 的说明:
     * 它标 {@code @Component} 时只有 service-auth 扫得到, 其他服务拦截器抛出的
     * {@code Authentication_NOT_LOGIN} 是个未注册的键, 会被渲染成"成功"。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthRsm authRsm() {
        return new AuthRsm();
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new AuthorizationInterceptor())
                .addPathPatterns("/**");
    }
}
