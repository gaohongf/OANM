package com.github.gaohongf.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import com.lingyun.base.response.ApiResponseDescriptor;
import com.lingyun.base.response.ResponseBuilder;
import com.lingyun.base.rsm.exception.RequestException;
import com.lingyun.base.rsm.message.Response;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * WebFlux 侧把 {@link RequestException} 渲染成 RSM 响应, 对应 servlet 侧的
 * {@code JsonResponseBodyPackerMvcAdapter.handleApiException}。
 *
 * <h2>为什么必须把状态码回调传进去</h2>
 * RSM 1.0.2 的 {@link ResponseBuilder} 接收 {@link ApiResponseDescriptor} 和两个回调:
 * 一个设状态码、一个写响应头。<b>HTTP 状态码只能通过前者写到响应上</b> —— 每个消息键都带一个
 * 状态码（{@code @RsmInfo} 的 {@code status}, 注解上是必填的; 库自己那版
 * {@code MessageResponseBuilder} 也是这么做的, 已对着字节码确认）。
 * 不传回调的话, 响应体写着"禁止访问"、HTTP 状态却是 200: 前端按 {@code response.ok} 判断,
 * 会把鉴权失败当成一条正常的流去解析（踩过 —— 页面显示"AI 没有返回任何内容"）。
 *
 * <h2>为什么不用再包适配器</h2>
 * 新版 API 不再要求传入某个 Web 栈的 {@code ServerHttpResponse} 对象, 而是直接收
 * {@code httpResponse::setStatusCode} / {@code httpResponse::getHeaders} 这样的回调,
 * reactive 的 {@code org.springframework.http.server.reactive.ServerHttpResponse} 可以直接对接;
 * 原来那个把 reactive 响应包成 servlet {@code ServerHttpResponse} 的适配器已经删除。
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
        ServerHttpResponse httpResponse = exchange.getResponse();
        Response response = responseBuilder.build(new ApiResponseDescriptor(ex.getMsgId(), null, ex.getVarargs()),
                httpResponse::setStatusCode, httpResponse::getHeaders);

        return Mono.just(response);
    }
}
