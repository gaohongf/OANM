package com.github.gaohongf.auth.register;


import java.util.List;

import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.auth.annotation.LoginOnly;

/**
 * 本服务"需要权限"的端点清单，从 {@link RequestMappingHandlerMapping} 扫出来。
 *
 * <h2>什么算"需要权限"</h2>
 * 排除两类，因为给它们建权限行没有意义 —— 权限行的语义是"可授予"，而这两类不需要授予：
 * <ul>
 *   <li>{@link IsOpen}：完全公开，本来就不鉴权</li>
 *   <li>{@link LoginOnly}：只要登录，不需要具体权限</li>
 * </ul>
 * 登记它们只会让权限列表里堆一堆没人该被授予的条目。
 *
 * <h2>为什么同时保留"方法"和"路径"</h2>
 * 权限键是 `METHOD:路径模式`（与鉴权拦截器的推导方式一致），登记时用键；
 * 但自检要拿"路径模式"去和网关路由比，所以两个都要留着。
 */
public record ServiceEndpoints(List<Endpoint> endpoints) {

    /**
     * 一个端点。
     *
     * @param httpMethod HTTP 方法，大写
     * @param pattern    路径模式，如 {@code /api/ops/work_order/{id}}
     */
    public record Endpoint(String httpMethod, String pattern) {

        /** 权限键，与 {@code AuthorizationInterceptor} 推导出来的形式一致 */
        public String permissionKey() {
            return httpMethod + ":" + pattern;
        }

        @Override
        public String toString() {
            return permissionKey();
        }
    }


    /** 全部权限键 */
    public List<String> permissionKeys() {
        return endpoints.stream().map(Endpoint::permissionKey).toList();
    }
}
