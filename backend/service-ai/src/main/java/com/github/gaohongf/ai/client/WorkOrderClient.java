package com.github.gaohongf.ai.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.lingyun.base.rsm.exception.RequestException;

import reactor.core.publisher.Mono;

/**
 * 调用工单服务建单。
 *
 * <p>
 * 透传浏览器的 {@code satoken}：建单这件事仍然由工单服务做权限校验、写 {@code createBy}
 * 审计、发建单通知 —— AI 服务只是个转发者，不应该拥有这些能力，更不应该替用户提权。
 *
 * <p>
 * 用 {@code exchangeToMono} 而不是 {@code retrieve()}：工单服务在 4xx/5xx 时同样返回
 * RSM 信封，我们要把里面的 {@code msg} 原样带给用户，而不是抛一个
 * {@code WebClientResponseException} 再变成"服务器开小差"。
 */
@Component
public class WorkOrderClient {

    /** 与前端 http.ts / sse.ts 发送的登录头一致 */
    private static final String TOKEN_HEADER = "satoken";

    private final WebClient webClient;

    /**
     * 注入的是 {@link WorkOrderClientConfiguration} 里那个专用的 WebClient ——
     * 负载均衡只作用于它，不要把负载均衡 Builder 注册成全局 bean（原因见那个配置类）。
     */
    public WorkOrderClient(WebClient workOrderWebClient) {
        this.webClient = workOrderWebClient;
    }

    public Mono<WorkOrderCreated> create(String token, WorkOrderCreateCommand command) {
        return webClient.post()
                .uri("/api/ops/work_order")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(command)
                .exchangeToMono(response -> response
                        .bodyToMono(new ParameterizedTypeReference<RsmEnvelope<WorkOrderCreated>>() {
                        })
                        .switchIfEmpty(Mono.error(() -> new RequestException(
                                "工单服务没有返回内容（HTTP " + response.statusCode().value() + "）")))
                        .onErrorMap(DecodingException.class, ex -> new RequestException(
                                "工单服务返回了无法解析的响应（HTTP " + response.statusCode().value() + "）"))
                        .flatMap(envelope -> {
                            if (!envelope.success()) {
                                String msg = envelope.msg();
                                return Mono.error(new RequestException(
                                        StringUtils.hasText(msg) ? msg : "工单创建失败"));
                            }
                            if (envelope.data() == null || !StringUtils.hasText(envelope.data().id())) {
                                return Mono.error(new RequestException("工单服务没有返回工单编号"));
                            }
                            return Mono.just(envelope.data());
                        }));
    }
}
