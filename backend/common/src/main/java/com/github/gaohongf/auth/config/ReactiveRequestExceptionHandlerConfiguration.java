package com.github.gaohongf.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import com.github.gaohongf.web.adapter.ServerHttpResponseAdapter;
import com.lingyun.base.rsm.ResponseBuilder;
import com.lingyun.base.rsm.exception.RequestException;
import com.lingyun.base.rsm.message.Response;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * WebFlux 侧把 {@link RequestException} 渲染成 RSM 响应, 对应 servlet 侧的
 * {@code JsonResponseBodyPackerMvcAdapter.handleApiException}。
 *
 * <h2>为什么必须用带响应对象的那个 build 重载</h2>
 * {@link ResponseBuilder} 有两个 build: 一个只收消息键, 一个多收一个
 * {@code ServerHttpResponse}。<b>HTTP 状态码只有后者会写到响应上</b> —— 每个消息键都带一个
 * 状态码（{@code @RsmInfo} 的 {@code status}, 注解上是必填的; 库自己那版
 * {@code MessageResponseBuilder} 也只用它来 {@code setStatusCode}, 已对着字节码确认）。
 * 用前者的话, 响应体写着"禁止访问"、HTTP 状态却是 200: 前端按 {@code response.ok} 判断,
 * 会把鉴权失败当成一条正常的流去解析（踩过 —— 页面显示"AI 没有返回任何内容"）。
 *
 * <h2>那个 ServerHttpResponse 是 servlet 侧的, 这正是适配器存在的原因</h2>
 * RSM 的签名用的是 {@code org.springframework.http.server.ServerHttpResponse}（spring-web,
 * servlet 侧那套）, 与 WebFlux 的 {@code org.springframework.http.server.reactive.ServerHttpResponse}
 * <b>同名但毫无继承关系</b>, 在 WebFlux 里直接传 reactive 的响应编译都过不了。
 * servlet 侧的做法是拿 {@code ServletServerHttpResponse} 包一层 {@code HttpServletResponse},
 * 这里用 {@link ServerHttpResponseAdapter} 包一层 reactive 响应 —— 两边是同一个做法。
 *
 * <h2>为什么必须限定 reactive</h2>
 * common 也在 servlet 服务（service-auth / service-work-order）的 classpath 上, 而那边的异常
 * 由 RSM 自己的 servlet 适配器渲染。不限定的话这个 advice 会一并参与 servlet MVC 的异常匹配:
 * 要么和库里那个抢同一个 {@code RequestException}, 要么（真抢到时）把本来就带状态码的响应退化成
 * 200; 更糟的是 {@code ServerWebExchange} 在 servlet MVC 里没有任何参数解析器 ——
 * 方法一旦被选中就直接是 500。所以与隔壁 {@code ReactiveAuthorizationAutoConfiguration}
 * 同一条约束: 只对 reactive 应用生效。
 */
@AllArgsConstructor
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveRequestExceptionHandlerConfiguration {

    private final ResponseBuilder<Response> responseBuilder;

    @ExceptionHandler(RequestException.class)
    public Mono<Response> handle(RequestException ex, ServerWebExchange exchange) {
        Response response = responseBuilder.build(
                new ServerHttpResponseAdapter(exchange.getResponse()),
                ex.getMsgId(), null, ex.getVarargs());
        return Mono.just(response);
    }
}
