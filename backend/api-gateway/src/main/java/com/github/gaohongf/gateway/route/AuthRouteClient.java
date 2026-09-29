package com.github.gaohongf.gateway.route;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 从 service-auth 拉取动态路由。
 *
 * <h2>为什么这里自己定义路径常量与响应包装</h2>
 * 这些常量在 {@code common} 里已经有一份（{@code AuthInternalApi.ROUTES}、
 * {@code ApiResponse}）, 但网关<b>不能依赖 common</b>: common 带的是
 * {@code spring-boot-starter-web} + {@code sa-token-spring-boot3-starter} + rsm 的 MVC starter,
 * 全是 Servlet 技术栈, 而网关是 WebFlux —— 两者同 classpath 会因 spring-webmvc 直接启动失败。
 * <p>
 * 所以这里是<b>有意的复制</b>。代价是路径改了要两边一起改, 收益是网关的技术栈保持干净。
 * 这也是项目既有约定的一部分（common 只服务业务模块, 网关需要时自己手动引入）。
 */
@Slf4j
@Component
public class AuthRouteClient {

    /** 与 common 的 {@code AuthInternalApi.ROUTES} 保持一致 */
    private static final String ROUTES_PATH = "/auth/internal/routes";

    /** 与 common 的 {@code AuthInternalApi.ROUTES_VERSION} 保持一致 */
    private static final String ROUTES_VERSION_PATH = "/auth/internal/routes/version";

    private final WebClient webClient;

    public AuthRouteClient(WebClient.Builder loadBalancedWebClientBuilder,
                           GatewayRouteProperties properties) {
        // http://<服务名>/... 由 LoadBalancer 解析成真实实例, 所以不写死 IP
        this.webClient = loadBalancedWebClientBuilder
                .baseUrl("http://" + properties.getServiceName())
                .build();
    }

    /**
     * 拉取全量启用中的路由。
     * <p>
     * 返回的是<b>全量快照</b>, 调用方用它整体替换内存里的路由集合, 所以这个操作是幂等的。
     */
    public Mono<List<RouteDefinition>> fetchRoutes() {
        return webClient.get()
                .uri(ROUTES_PATH)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<AuthApiResponse<List<RouteDefinition>>>() {
                })
                .map(response -> {
                    requireSuccess(response, "拉取路由");
                    return response.data() == null ? List.<RouteDefinition>of() : response.data();
                });
    }

    /**
     * 拉取路由版本号。
     * <p>
     * 只为了判断"变了没有" —— 版本号一致就不必拉全量路由。
     */
    public Mono<String> fetchVersion() {
        return webClient.get()
                .uri(ROUTES_VERSION_PATH)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<AuthApiResponse<String>>() {
                })
                .map(response -> {
                    requireSuccess(response, "拉取路由版本号");
                    return response.data() == null ? "0" : response.data();
                });
    }

    /**
     * RSM 的统一响应包装 {@code {code,data,msg,type}}。
     * <p>
     * 判成功用 {@code type} 而不是 {@code code}: code 是消息 id 派生的（同一个接口在不同
     * 场景下可能是 1053/1054/1056）, 没有稳定的白名单; 而 {@code type} 只有
     * SUCCESS/WARN/INFO/ERROR 四种取值, 语义明确。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuthApiResponse<T>(String type, Integer code, T data, String msg) {

        boolean isSuccess() {
            return "SUCCESS".equalsIgnoreCase(type);
        }
    }

    private static void requireSuccess(AuthApiResponse<?> response, String action) {
        if (response == null || !response.isSuccess()) {
            // 抛出去交给调用方决定怎么降级（通常是保留 last-known-good）
            throw new IllegalStateException(action + "失败: "
                    + (response == null ? "响应为空" : "type=" + response.type() + " msg=" + response.msg()));
        }
    }
}
