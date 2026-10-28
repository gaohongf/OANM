package com.github.gaohongf.ai.client;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.github.gaohongf.ai.entity.dto.KnowledgeHit;
import com.github.gaohongf.ai.entity.dto.KnowledgeRetrieveResult;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 调用知识库服务的内部检索接口。
 *
 * <p>
 * 检索失败（服务没起来、嵌入模型不可用、超时）一律返回空列表：自助解决节点据此跳过，
 * 用户仍然可以走原来的工单流程。知识库是"能帮忙更好"的旁路，不能成为提单的前置条件。
 *
 * <p>
 * 接口不在网关下（{@code /knowledge/internal/**}），只在内网通过 Nacos 服务名调用。
 */
@Slf4j
@Component
public class KnowledgeClient {

    private final WebClient knowledgeWebClient;

    public KnowledgeClient(@Qualifier("knowledgeWebClient") WebClient knowledgeWebClient) {
        this.knowledgeWebClient = knowledgeWebClient;
    }

    /**
     * @return 命中的片段；失败时为空列表，不会抛异常
     */
    public List<KnowledgeHit> retrieve(String query, int topK, Duration timeout) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("topK", topK);

        try {
            List<KnowledgeHit> hits = knowledgeWebClient.post()
                    .uri("/knowledge/internal/retrieve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(response -> response
                            .bodyToMono(new ParameterizedTypeReference<RsmEnvelope<KnowledgeRetrieveResult>>() {
                            })
                            .onErrorMap(DecodingException.class,
                                    ex -> new IllegalStateException("知识库返回了无法解析的响应", ex))
                            .flatMap(envelope -> {
                                if (!envelope.success()) {
                                    return Mono.error(new IllegalStateException(
                                            "知识库检索失败: " + envelope.msg()));
                                }
                                KnowledgeRetrieveResult data = envelope.data();
                                return Mono.just(data == null || data.getHits() == null
                                        ? List.<KnowledgeHit>of()
                                        : data.getHits());
                            }))
                    .block(timeout);
            return hits == null ? List.of() : hits;
        } catch (RuntimeException ex) {
            log.warn("知识库检索不可用，跳过自助解决: {}", ex.getMessage());
            return List.of();
        }
    }
}
