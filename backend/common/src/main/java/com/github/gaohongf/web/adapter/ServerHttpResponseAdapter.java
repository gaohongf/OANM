package com.github.gaohongf.web.adapter;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * 把 WebFlux 的响应
 * （{@code org.springframework.http.server.reactive.ServerHttpResponse}）适配成 servlet 侧的
 * {@link ServerHttpResponse}, 也就是 {@code ServletServerHttpResponse} 在 reactive 侧的对应物。
 *
 * <h2>为什么需要它</h2>
 * 两个接口<b>同名但没有任何关系</b>, 一个在 {@code org.springframework.http.server}、一个在
 * {@code ...server.reactive} 下。RSM 的
 * {@code ResponseBuilder.build(ServerHttpResponse, String, Object, Object[])} 收的是 servlet 那个,
 * 在 WebFlux 里根本拿不到这种对象 —— 不包一层连编译都过不了。
 * servlet 侧是拿 {@code ServletServerHttpResponse} 包一层 {@code HttpServletResponse} 再传进去的,
 * 这里包的是 reactive 响应: 同一个做法, 只是被包的那层不同。
 *
 * <h2>为什么只实现 setStatusCode 就够用</h2>
 * 那个 build 重载对响应<b>只做一件事</b>: 把消息键上的 HTTP 状态码写上去（库自己那版
 * {@code MessageResponseBuilder} 也是, 对着字节码确认过）。响应体不经过这个接口 ——
 * 它是 {@code build(...)} 的<b>返回值</b>, 由 Spring 自己的序列化管线写出去。
 * 所以 {@link #getBody()} / {@link #flush()} 没有调用方: 与其写一个语义不通的实现
 * （servlet 的 {@code OutputStream} 和 reactive 的 {@code Flux<DataBuffer>} 没法互转）,
 * 不如让真的有人用错时当场炸掉, 而不是安静地写出一半的响应。
 * <p>
 * 另: 响应已提交时 reactive 的 {@code setStatusCode} 是<i>静默忽略</i>且返回 false 的,
 * 而 servlet 接口这个方法返回 void —— 拿不到那个 false, 也没得可做（提交之后改不了状态码）。
 */
public class ServerHttpResponseAdapter implements ServerHttpResponse {
    private final org.springframework.http.server.reactive.ServerHttpResponse response;

    public ServerHttpResponseAdapter(org.springframework.http.server.reactive.ServerHttpResponse response) {
        this.response = response;
    }

    @Override
    public OutputStream getBody() throws IOException {
        
        throw new UnsupportedOperationException("RSM 只通过本适配器设置状态码, 不写响应体; 见类注释");
    }

    @Override
    public HttpHeaders getHeaders() {
        return response.getHeaders();
    }

    @Override
    public void close() {
        response.setComplete();
    }

    @Override
    public void flush() throws IOException {
        throw new UnsupportedOperationException("RSM 只通过本适配器设置状态码, 不写响应体; 见类注释");
    }

    @Override
    public void setStatusCode(HttpStatusCode status) {
        response.setStatusCode(status);
    }

}
