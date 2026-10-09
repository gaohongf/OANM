package com.github.gaohongf.auth.interceptor;

import java.util.Locale;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.server.ServerWebExchange;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.auth.annotation.LoginOnly;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.lingyun.base.rsm.HttpStatusRsm;
import com.lingyun.base.rsm.exception.RequestException;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * WebFlux 侧的鉴权, 与 servlet 侧的 {@code ServletAuthorizationInterceptor} 一一对应。
 *
 * <h2>为什么挂在 HandlerAdapter 上, 而不是 WebFilter 上</h2>
 * 权限键是 {@code METHOD:路径模式}（如 {@code GET:/api/ops/work_order/{id}}），而"路径模式"
 * 要等 HandlerMapping 把请求匹配到具体方法之后才有（{@code BEST_MATCHING_PATTERN_ATTRIBUTE}）。
 * 放在 WebFilter 里能拿到的只有具体 URI, 带路径参数的接口就永远匹配不上库里的模式。
 *
 * <h2>校验必须离开事件循环线程（这是踩过的坑）</h2>
 * 这一段校验是<b>同步阻塞</b>的: sa-token 的 {@code StpInterface} 就是同步接口, 而
 * {@code RemoteStpInterface} 取授权还要发一次 Feign 远程调用（本地缓存 30 秒, 命中时是纯内存）;
 * 用 Redis 存会话时, {@code StpUtil.isLogin()} 读 session 同样是阻塞 I/O。
 * <p>
 * 而 {@code handle()} 跑在 reactor-netty 的<b>事件循环</b>上（线程名 {@code reactor-http-nio-x}），
 * 在那里阻塞会被 reactor 直接判错:
 * <pre>
 *   block()/blockFirst()/blockLast() are blocking, which is not supported in
 *   thread reactor-http-nio-3
 * </pre>
 * 它来自 Feign 选实例那一步 —— {@code BlockingLoadBalancerClient.choose()} 里是裸的
 * {@code Mono.from(loadBalancer.choose(request)).block()}（已从 jar 字节码确认）。
 * <p>
 * 这个错<b>表现得很不像它的成因</b>: {@code RemoteStpInterface} 把这次异常当成"取不到授权",
 * 按"无任何权限"处理（那是它刻意的安全侧取舍）, 于是持有任何权限都 403 —— 连超管也一样,
 * 唯一线索只有一行 WARN。所以校验整段挪到 {@code boundedElastic}: 那正是 reactor 为
 * "要阻塞的活"准备的线程池。
 *
 * <h2>为什么用 defer 包一层</h2>
 * {@code subscribeOn} 只决定"订阅发生在哪个线程", 而 {@code defer} 的工厂函数恰好就在
 * 那个线程上执行 —— {@code SaReactorSyncHolder} 的上下文是 ThreadLocal 的,
 * 只有让 setContext / 校验 / clearContext 落在同一个线程上才成立。
 */
@AllArgsConstructor
@Slf4j
public class ReactiveAuthorizationRequestMappingHandlerAdapter extends RequestMappingHandlerAdapter {
    // private static final Object[] EMPTY_ARGS = new Object[0];
    // private final ResponseBuilder<Response> responseBuilder;

    @Override
    public @NonNull Mono<HandlerResult> handle(ServerWebExchange exchange, Object handler) {
        // 挪线程的完整理由见类注释。注意这里挪的是"整段处理", 不只是校验: super.handle 返回的
        // Mono 是在这个线程上被订阅的, 于是控制器方法也在 boundedElastic 上执行 —— 对
        // 本模块的 AI 流式接口没有影响（响应仍由 netty 的事件循环写回）, 但反过来意味着
        // 在控制器里做阻塞调用是安全的。这与 servlet 侧（tomcat 工作线程本来就允许阻塞）一致。
        return Mono.defer(() -> authorize(exchange, handler))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 同步的校验段。调用它的线程必须是能阻塞的线程, 见 {@link #handle}。 */
    private Mono<HandlerResult> authorize(ServerWebExchange exchange, Object handler) {
        try {
            SaReactorSyncHolder.setContext(exchange);
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            if (handlerMethod.getMethodAnnotation(IsOpen.class) != null) {
                return super.handle(exchange, handler);
            }
            if (!StpUtil.isLogin()) {
                return Mono.error(() -> new RequestException(AuthRsm.NOT_LOGIN));
            }

            // 第三档: 只要登录, 不要具体权限。放在登录判断之后 —— 它仍然要求已登录,
            // 只是不再往下走权限比对。典型用途是 /api/auth/me: 任何已登录用户都得能拿到
            // 自己的菜单和权限, 包括一个角色都没有的新用户。
            if (handlerMethod.getMethodAnnotation(LoginOnly.class) != null) {
                return super.handle(exchange, handler);
            }

            String permissionKey = permissionKey(exchange);
            if (permissionKey == null) {
                // 不是由 @RequestMapping 方法处理的请求（静态资源等）不该走到这里;
                // 真走到了说明部署形态和预期不符, 记一条日志再拒绝, 别静默放行。
                log.warn("解析不出权限键, 按无权限拒绝: {} {}",
                        exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath());
                return Mono.error(() -> new RequestException(HttpStatusRsm.FORBIDDEN));
            }

            if (StpUtil.hasPermission(permissionKey)) {
                return super.handle(exchange, handler);
            }

            log.debug("权限不足: 用户 {} 缺少 {}", StpUtil.getLoginIdDefaultNull(), permissionKey);
            return Mono.error(() -> new RequestException(HttpStatusRsm.FORBIDDEN));
        } finally {
            SaReactorSyncHolder.clearContext();
        }

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
}
