package com.github.gaohongf.web.rsm;

import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import com.lingyun.base.response.ApiResponseDescriptor;
import com.lingyun.base.response.ResponseBuilder;
import com.lingyun.base.rsm.message.Response;

/**
 * WebFlux 侧 {@code @RequestMapping} 端点的响应信封构造器。
 *
 * <h2>为什么反应式端点要自己拼信封</h2>
 * servlet 侧不用: 那边有一个 {@code JsonResponseBodyPackerMvcAdapter}(rsm-mvc) 作为
 * {@code ResponseBodyAdvice} 自动包装每个返回值。但那个类是
 * <b>servlet 的</b> {@code ResponseBodyAdvice}, 而 <b>WebFlux 根本没有这个扩展点</b> ——
 * 对着 classpath 上的 {@code spring-webflux-6.2.19.jar} 确认过: 整个 jar 里
 * <b>没有任何一处</b>引用 {@code ResponseBodyAdvice} 这个类型。于是 common 带给所有服务的
 * 那个 packer 在反应式服务里只是上下文里一个没人调的 bean。
 * <p>
 * 直接后果: 反应式端点返回 {@code PageRes}、{@code Long} 这类普通对象时, 响应体就是那个对象本身,
 * <b>没有</b> {@code {type,code,data,msg}} 信封。而前端的 {@code request<T>()} 是按信封拆的
 * （它连判成功都是看 {@code type === 'SUCCESS'}）, 于是会报"响应格式不符合预期"。
 * 这件事没有静默的中间状态: 要么在这里拼, 要么前端每个调用点都写一次特例。
 *
 * <h2>为什么不干脆在 {@code RequestMappingHandlerAdapter} 里统一包一层</h2>
 * 本仓库确实已经子类化了那个适配器（{@code ReactiveAuthorizationRequestMappingHandlerAdapter}）,
 * 在那里包是最像 servlet 侧的做法。但它必须能区分"该包的 JSON 返回值"和"绝不能碰的流":
 * 同一个服务里 {@code Flux<ServerSentEvent>} 和普通对象是混着返回的
 * （service-message 自己就是: 收件箱接口 + {@code /stream}）,
 * 包装器一旦手滑, 流就再也流不出去, 而且症状是"请求一直挂着"。
 * 所以选择让反应式端点<b>显式</b>声明自己的信封 —— 多一次方法调用, 换掉一整类只在运行时暴露的错误。
 *
 * <h2>为什么要把状态码回调传进去</h2>
 * 与 {@code ReactiveRequestExceptionHandlerConfiguration} 同一条理由: <b>HTTP 状态码只能通过
 * 状态回调写到响应上</b>（取自消息键的 {@code @RsmInfo.status}）。只构造响应体、不接响应对象,
 * 响应体会写着"查询成功"而 HTTP 状态是别的值。
 *
 * @see com.github.gaohongf.auth.config.ReactiveRequestExceptionHandlerConfiguration
 *      失败路径上的同一个做法
 */
public class ReactiveRsmWriter {

    private final ResponseBuilder<Response> responseBuilder;

    public ReactiveRsmWriter(ResponseBuilder<Response> responseBuilder) {
        this.responseBuilder = responseBuilder;
    }

    /**
     * 构造一个成功响应。
     *
     * @param exchange   当前请求的 exchange, 用来拿响应对象设状态码
     * @param messageKey 消息键, 用 {@code GenericRsm} 里那几个常量
     *                   （{@code QUERY_SUCCESS} / {@code CREATE_SUCCESS} /
     *                   {@code UPDATE_SUCCESS}）,
     *                   与 servlet 侧 {@code @ExecutionSuccess} 取的是同一批键 ——
     *                   这样两条路的 {@code type}/{@code msg} 完全一致
     * @param data       业务数据, 可为 null
     * @return 已经带好状态码的响应体, 直接作为控制器返回值即是最终 JSON
     */
    public Response success(ServerWebExchange exchange, String messageKey, Object data) {
        // 成功消息的模板不带占位符, 第三项（模板实参）传 null 即可。
        ServerHttpResponse response = exchange.getResponse();
        return responseBuilder.build(new ApiResponseDescriptor(messageKey, data, null),
                response::setStatusCode,
                response::getHeaders);
    }
}
