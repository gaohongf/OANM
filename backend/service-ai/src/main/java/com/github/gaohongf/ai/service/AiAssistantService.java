package com.github.gaohongf.ai.service;

import java.util.List;

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
     *
     * @param images 本轮用户附带的图片访问地址（相对路径），可为空。
     *               当前只写入工作流状态，供下一步的识图节点读取，本轮的模型调用不消费它们。
     */
    Flux<ServerSentEvent<String>> workOrderUserInputIntentInference(String id, String userInput, List<String> images);

    /**
     * 用户选中某个意图后，把整段对话整理成一张工单草稿。
     *
     * <p>
     * 必须带上一轮的 {@code id}：图从该会话的中断点恢复，草稿要基于同一段记忆。
     */
    Flux<ServerSentEvent<String>> workOrderDraftGenerate(String id, Integer userSelect);

    /**
     * 用户在自助解决建议之后点击了「已经解决了」或「还没解决，帮我提工单」。
     *
     * @param action {@code SELF_RESOLVED} 或 {@code WORK_ORDER}
     */
    Flux<ServerSentEvent<String>> workOrderDecision(String id, String action);

    /**
     * 用户确认草稿后提交建单。
     *
     * @param token 浏览器带来的 {@code satoken}，原样透传给工单服务
     */
    Mono<WorkOrderCreated> submitWorkOrder(AiWorkOrderSubmitCommand command, String token);
}
