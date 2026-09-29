package com.github.gaohongf.auth.interceptor;

import java.util.Locale;

import org.springframework.lang.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.auth.annotation.LoginOnly;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.lingyun.base.rsm.HttpStatusRsm;
import com.lingyun.base.rsm.R;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 接口鉴权拦截器：权限键 = {@code METHOD:路径模式}, 如 {@code GET:/api/ops/work_order/{id}}。
 *
 * <h2>为什么用"路径模式"而不是请求 URI</h2>
 * 原实现是 {@code StpUtil.hasPermission(method + ":" + request.getRequestURI())}, 拿具体的
 * {@code GET:/api/ops/work_order/1} 去比对 —— 这意味着数据库里必须存一条字面量
 * {@code GET:/api/ops/work_order/1} 才能拦住它, 换个 id 就失效。带路径参数的接口因此根本拦不住。
 * <p>
 * 规范模式直接从 {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} 取 —— 这是 Spring 在
 * 匹配 handler 时就写进 request 的, 已经是"类级前缀 + 方法级路径"组合好的结果, 不需要自己拼注解、
 * 也不需要自己写模式匹配和评分。取到的是 {@code /api/ops/work_order/{id}}, 与权限表里的键逐字相同。
 *
 * <h2>为什么不能直接用 StpUtil.hasPermission 判未登录</h2>
 * 它内部会 {@code catch (NotLoginException) → return false}（已反编译确认）, 所以未登录和
 * 无权限都表现为 false。不先单独判登录的话, 未认证的请求会收到 403 ——
 * 前端没法区分"该跳登录页"和"你真的没这个权限", 只能一律当失败处理。
 *
 * <h2>路径约定</h2>
 * 权限键里的路径是<b>服务内部</b>的路径。按项目约定, 对外接口的服务内路径与其外部路径一致
 * （都以 {@code /api/} 开头）, 所以这里不需要关心网关怎么转发。
 */
@Slf4j
public class AuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        // 只处理最初的请求分发。
        // ERROR 分发（Spring 转发到 /error 处理错误）也是一个由 HandlerMethod 处理的请求，
        // 不排除掉的话：一个本该 404 的路径会被拦成"未登录"，把真实的 404 掩盖掉 ——
        // 排查时会朝着鉴权方向查半天，而问题其实是路由写错了。
        // FORWARD / ASYNC 同理不该重复鉴权。
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }

        // 只鉴权控制器方法。
        // 非 HandlerMethod 的处理器（最典型的是 Spring Boot 默认给 /** 注册的
        // ResourceHttpRequestHandler, 它负责静态资源）不是业务端点, 没有权限键可言。
        // 不排除掉的话, 访问一个不存在的路径会先匹配到静态资源处理器、从而被报成 401,
        // 把本该有的 404 掩盖掉 —— 排查时会朝鉴权方向白查半天。
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (handlerMethod.getMethodAnnotation(IsOpen.class) != null) {
            return true;
        }

        if (!StpUtil.isLogin()) {
            return R.error(AuthRsm.NOT_LOGIN);
        }

        // 第三档: 只要登录, 不要具体权限。放在登录判断之后 —— 它仍然要求已登录,
        // 只是不再往下走权限比对。典型用途是 /api/auth/me: 任何已登录用户都得能拿到
        // 自己的菜单和权限, 包括一个角色都没有的新用户。
        if (handlerMethod.getMethodAnnotation(LoginOnly.class) != null) {
            return true;
        }

        String permissionKey = permissionKey(request);
        if (permissionKey == null) {
            // 不是由 @RequestMapping 方法处理的请求（静态资源等）不该走到这里;
            // 真走到了说明部署形态和预期不符, 记一条日志再拒绝, 别静默放行。
            log.warn("无法为请求 {} {} 推导出权限键, 已拒绝。"
                            + "这通常意味着该请求不是由 @RequestMapping 方法处理的。",
                    request.getMethod(), request.getRequestURI());
            return R.error(HttpStatusRsm.FORBIDDEN);
        }

        if (StpUtil.hasPermission(permissionKey)) {
            return true;
        }

        log.debug("权限不足: 用户 {} 缺少 {}", StpUtil.getLoginIdDefaultNull(), permissionKey);
        return R.error(HttpStatusRsm.FORBIDDEN);
    }

    /**
     * 推导本次请求对应的权限键。
     *
     * @return {@code METHOD:路径模式}; 连请求方法都取不到时返回 {@code null}
     */
    private static String permissionKey(HttpServletRequest request) {
        String method = request.getMethod();
        if (method == null || method.isBlank()) {
            return null;
        }

        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = pattern instanceof String text && !text.isBlank()
                ? text
                // 兜底: 万一 Spring 没写这个属性, 退化成"具体 URI"。
                // 这不是等价的 —— 只有"数据库里存的就是具体路径"的接口才认得出来,
                // 带路径参数的接口会因此 403（安全侧失败, 不会误放行）。
                : request.getRequestURI();

        return method.toUpperCase(Locale.ROOT) + ":" + path;
    }
}
