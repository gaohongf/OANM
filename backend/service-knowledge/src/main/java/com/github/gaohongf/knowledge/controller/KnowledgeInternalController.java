package com.github.gaohongf.knowledge.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.knowledge.entity.req.RetrieveCommand;
import com.github.gaohongf.knowledge.entity.res.KnowledgeRetrieveRes;
import com.github.gaohongf.knowledge.service.KnowledgeSearchService;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * 供其他服务调用的检索接口（本轮只交付接口，service-ai 的接入在下一轮）。
 *
 * <h2>为什么不在 {@code /api} 下</h2>
 * 网关只转发 {@code /api/**} 与 {@code /file/**}，放在 {@code /knowledge/internal}
 * 天然不会被外部通过网关访问。
 *
 * <h2>只靠网络边界的风险</h2>
 * 这里是 {@code @IsOpen}，8098 如果对局域网开放，任何能访问的人都能直接调用。
 * 知识内容敏感时再加共享密钥头，而不是把它挪回网关。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/knowledge/internal")
public class KnowledgeInternalController {

    private final KnowledgeSearchService searchService;

    /**
     * 检索最相关的片段。
     * <p>
     * 嵌入失败返回 503（{@code KNOWLEDGE_RETRIEVE_UNAVAILABLE}）而不是空列表：
     * 调用方需要能区分"确实没有相关内容"和"检索服务挂了"。
     */
    @IsOpen
    @PostMapping("/retrieve")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public KnowledgeRetrieveRes retrieve(@RequestBody @Valid RetrieveCommand command) {
        return searchService.retrieve(command);
    }
}
