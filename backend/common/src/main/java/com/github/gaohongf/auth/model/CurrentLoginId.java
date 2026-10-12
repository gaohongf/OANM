package com.github.gaohongf.auth.model;

import org.springframework.web.server.ServerWebExchange;

import com.github.gaohongf.auth.rsm.AuthRsm;
import com.lingyun.base.rsm.exception.RequestException;

/**
 * WebFlux 侧的"当前登录用户 id"，由鉴权适配器写进请求属性，控制器读出来。
 *
 * <h2>为什么不直接在控制器里调 {@code StpUtil.getLoginIdAsLong()}</h2>
 * 那样在 WebFlux 控制器里<b>一定取不到</b>，而且失败得很不像它的成因。原因链条:
 * <ol>
 *   <li>sa-token 在反应式栈上的上下文实现是
 *       {@code SaTokenContextForSpringReactor extends SaTokenContextForThreadLocal} ——
 *       名字里有 Reactor，但它自始至终用的是 <b>ThreadLocal</b>（该类只有构造器，没有任何覆写，已对着字节码确认）。</li>
 *   <li>过滤器 {@code SaTokenContextFilterForReactor} 只把 exchange 写进 Reactor Context
 *       （{@code contextWrite(SaReactorHolder::setContext)}），不碰那个 ThreadLocal。</li>
 *   <li>而 {@link com.github.gaohongf.auth.interceptor.ReactiveAuthorizationRequestMappingHandlerAdapter}
 *       在 {@code super.handle()} 返回<b>惰性</b> Mono 之后就地 {@code clearContext()}（它在 {@code finally} 里），
 *       所以控制器方法体被订阅执行时，那个 ThreadLocal 早就空了。</li>
 * </ol>
 * 于是控制器里调 sa-token 拿到的是"未登录/无上下文"，与真实的登录状态相反。
 *
 * <h2>为什么写进 exchange 的 attributes</h2>
 * 适配器里调用 {@code StpUtil} 的那一刻是<b>唯一可靠</b>的时机：它在 {@code boundedElastic} 上、
 * 上下文刚设好、线程本来就可以阻塞。把它放到 attributes 里是最小改动 —— 属性是普通请求作用域状态，
 * 之后在哪个线程读都成立（包括 SSE 的订阅过程）。
 * <p>
 * 副作用是好的：控制器里再也不需要问 sa-token "我是谁"，{@code ServerWebExchange} 是 WebFlux
 * 本来就能注入的参数类型。
 */
public final class CurrentLoginId {

    /**
     * 属性键。
     * <p>
     * 带 {@code oanm.} 前缀是为了与业务自己往 attributes 里放的东西分开 —— 那些键没有命名空间约定，
     * 短键（如 {@code "userId"}）撞上的概率不低。
     */
    public static final String ATTRIBUTE = "oanm.auth.loginId";

    private CurrentLoginId() {
    }

    /**
     * 取当前登录用户 id，取不到返回 {@code null}。
     * <p>
     * 取不到只有一种正常情况：端点标了 {@code @IsOpen}（鉴权整体跳过，适配器不会写这个属性）。
     * 需要登录的端点上拿到了 null，说明鉴权链的顺序被改坏了 —— 所以下面那个 {@link #require} 才是
     * 控制器该用的方法。
     */
    public static Long get(ServerWebExchange exchange) {
        Object value = exchange.getAttributes().get(ATTRIBUTE);
        return value instanceof Long id ? id : null;
    }

    /**
     * 取当前登录用户 id，取不到即拒绝。
     * <p>
     * 拒绝而不是返回 null：调用方拿到 null 之后大概率是"按 0 或按 null 查库"，那会查出一条
     * 谁都不属于的数据，或者更糟 —— 因为条件写成 {@code receiver = null} 而退化成"不过滤收件人"。
     * 直接 401 是唯一不会出错的默认行为。
     */
    public static Long require(ServerWebExchange exchange) {
        Long id = get(exchange);
        if (id == null) {
            throw new RequestException(AuthRsm.NOT_LOGIN);
        }
        return id;
    }
}
