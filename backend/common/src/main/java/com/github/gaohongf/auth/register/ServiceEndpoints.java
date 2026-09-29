package com.github.gaohongf.auth.register;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
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

    /**
     * `@RequestMapping` 没指定方法时，展开成这些方法。
     * <p>
     * 必须展开成具体方法而不能记成 `*:/path`：拦截器是按真实请求方法推导权限键的
     * （`GET:/path`、`POST:/path`…），记一个通配键谁也匹配不上。
     */
    private static final List<RequestMethod> ALL_METHODS = List.of(
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH);

    /**
     * Spring Boot 自带的错误页控制器。
     * <p>
     * 它是个 {@code @Controller}，所以会出现在 {@link RequestMappingHandlerMapping} 里，
     * 但它不是业务端点：它是 ERROR 分发时用的，而鉴权拦截器明确跳过非 REQUEST 分发。
     * 把它登记成权限会把 `/error` 变成一个可授予的权限项。
     * <p>
     * 用类名字符串比较而不是引用那个类：避免为了排除它而给 common 增加对
     * spring-boot-autoconfigure 内部类的编译期依赖。
     */
    private static final String ERROR_CONTROLLER = "org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController";

    /** 扫描。 */
    public static ServiceEndpoints scan(RequestMappingHandlerMapping handlerMapping) {
        Set<Endpoint> found = new LinkedHashSet<>();

        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo mapping = entry.getKey();
            HandlerMethod handler = entry.getValue();

            if (shouldSkip(handler)) {
                continue;
            }

            Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
            List<String> patterns = new ArrayList<>(mapping.getPatternValues());

            for (String pattern : patterns) {
                if (methods.isEmpty()) {
                    for (RequestMethod method : ALL_METHODS) {
                        found.add(new Endpoint(method.name(), pattern));
                    }
                } else {
                    for (RequestMethod method : methods) {
                        found.add(new Endpoint(method.name(), pattern));
                    }
                }
            }
        }

        return new ServiceEndpoints(List.copyOf(found));
    }

    private static boolean shouldSkip(HandlerMethod handler) {
        if (handler.getMethodAnnotation(IsOpen.class) != null
                || handler.getMethodAnnotation(LoginOnly.class) != null) {
            return true;
        }
        return ERROR_CONTROLLER.equals(handler.getBeanType().getName());
    }

    /** 全部权限键 */
    public List<String> permissionKeys() {
        return endpoints.stream().map(Endpoint::permissionKey).toList();
    }
}
