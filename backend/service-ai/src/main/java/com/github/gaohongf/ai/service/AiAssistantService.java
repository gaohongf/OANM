package com.github.gaohongf.ai.service;

import org.springframework.http.codec.ServerSentEvent;

import com.github.gaohongf.ai.client.WorkOrderCreated;
import com.github.gaohongf.ai.entity.req.AiWorkOrderSubmitCommand;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AiAssistantService {

    /**
     * 推断用户输入的意图：缺信息时追问，信息够了给出候选选项。
     *
     * <p>
     * {@code id} 为空表示新会话；带上之前的 id 表示在同一段对话里继续补充。
     */
    Flux<ServerSentEvent<String>> workOrderUserInputIntentInference(String id, String userInput);

    /**
     * 用户选中某个意图后，把整段对话整理成一张工单草稿。
     *
     * <p>
     * 必须带上一轮的 {@code id}：图从该会话的中断点恢复，草稿要基于同一段记忆。
     */
    Flux<ServerSentEvent<String>> workOrderDraftGenerate(String id, Integer userSelect);

    /**
     * 用户确认草稿后提交建单。
     *
     * @param token 浏览器带来的 {@code satoken}，原样透传给工单服务
     */
    Mono<WorkOrderCreated> submitWorkOrder(AiWorkOrderSubmitCommand command, String token);
}
