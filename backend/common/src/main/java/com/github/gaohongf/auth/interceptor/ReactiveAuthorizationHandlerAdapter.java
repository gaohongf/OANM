package com.github.gaohongf.auth.interceptor;

import java.util.Locale;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerAdapter;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.server.ServerWebExchange;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.auth.annotation.LoginOnly;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.lingyun.base.rsm.HttpStatusRsm;
import com.lingyun.base.rsm.exception.RequestException;

import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@Slf4j
public class ReactiveAuthorizationHandlerAdapter implements HandlerAdapter {

    private final RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    @Override
    public @NonNull Mono<HandlerResult> handle(ServerWebExchange exchange, Object handler) {
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        if (handlerMethod.getMethodAnnotation(IsOpen.class) != null) {
            return requestMappingHandlerAdapter.handle(exchange, handler);
        }
        if (!StpUtil.isLogin()) {
            return Mono.error(() -> new RequestException(AuthRsm.NOT_LOGIN));
        }

        // 第三档: 只要登录, 不要具体权限。放在登录判断之后 —— 它仍然要求已登录,
        // 只是不再往下走权限比对。典型用途是 /api/auth/me: 任何已登录用户都得能拿到
        // 自己的菜单和权限, 包括一个角色都没有的新用户。
        if (handlerMethod.getMethodAnnotation(LoginOnly.class) != null) {
            return requestMappingHandlerAdapter.handle(exchange, handler);
        }

        String permissionKey = permissionKey(exchange);
        if (permissionKey == null) {
            // 不是由 @RequestMapping 方法处理的请求（静态资源等）不该走到这里;
            // 真走到了说明部署形态和预期不符, 记一条日志再拒绝, 别静默放行。
            return Mono.error(() -> new RequestException(HttpStatusRsm.FORBIDDEN));
        }

        if (StpUtil.hasPermission(permissionKey)) {
            return requestMappingHandlerAdapter.handle(exchange, handler);
        }

        log.debug("权限不足: 用户 {} 缺少 {}", StpUtil.getLoginIdDefaultNull(), permissionKey);
        return Mono.error(() -> new RequestException(HttpStatusRsm.FORBIDDEN));
    }

    private static String permissionKey(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        Object pattern = request.getAttributes().get(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = pattern instanceof String text && !text.isBlank()
                ? text
                // 兜底: 万一 Spring 没写这个属性, 退化成"具体 URI"。
                // 这不是等价的 —— 只有"数据库里存的就是具体路径"的接口才认得出来,
                // 带路径参数的接口会因此 403（安全侧失败, 不会误放行）。
                : request.getURI().getPath();

        return method.name().toUpperCase(Locale.ROOT) + ":" + path;
    }

    @Override
    public boolean supports(Object handler) {
        return handler instanceof HandlerMethod;
    }

}
