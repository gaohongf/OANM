package com.github.gaohongf.ai.service.impl;

import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.gaohongf.ai.chat.sse.ChatStartEvent;
import com.github.gaohongf.ai.client.WorkOrderClient;
import com.github.gaohongf.ai.client.WorkOrderCreateCommand;
import com.github.gaohongf.ai.client.WorkOrderCreated;
import com.github.gaohongf.ai.entity.req.AiWorkOrderSubmitCommand;
import com.github.gaohongf.ai.graph.wo.WorkOrderWorkflowLauncher;
import com.github.gaohongf.ai.service.AiAssistantService;
import com.github.gaohongf.web.sse.SseElement;
import com.github.gaohongf.web.util.Sse;
import com.lingyun.base.rsm.exception.RequestException;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private final OpenAiChatProperties openAiChatProperties;
    private final WorkOrderWorkflowLauncher workOrderWorkflowLauncher;
    private final WorkOrderClient workOrderClient;

    @Override
    public Flux<ServerSentEvent<String>> workOrderUserInputIntentInference(String id, String userInput) {
        if (!StringUtils.hasText(userInput)) {
            return errorFrame(new RequestException("用户输入不可为空"));
        }
        return intentStream(workOrderWorkflowLauncher.stream(id, userInput));
    }

    @Override
    public Flux<ServerSentEvent<String>> workOrderDraftGenerate(String id, Integer userSelect) {
        if (!StringUtils.hasText(id)) {
            return errorFrame(new RequestException("缺少会话标识，请先描述问题"));
        }
        if (userSelect == null) {
            return errorFrame(new RequestException("缺少用户选择"));
        }
        return intentStream(workOrderWorkflowLauncher.select(id, userSelect));
    }

    /**
     * 把图的事件流包成完整的一轮 SSE：开头报模型，结尾报成功。
     *
     * <p>
     * 末尾统一接错误帧：图里任何节点抛异常，浏览器都会收到一条可读的 error，
     * 而不是看到连接莫名其妙断掉、界面一直转圈。
     */
    private Flux<ServerSentEvent<String>> intentStream(Flux<SseElement> events) {
        String modelName = openAiChatProperties.getOptions().getModel();
        return Flux.concat(
                Mono.fromCallable(() -> Sse.message(new ChatStartEvent(modelName, System.currentTimeMillis()))),
                events.map(Sse::message),
                Mono.fromCallable(() -> Sse.done(Sse.DONE_REASON_SUCCESS)))
                .onErrorResume(AiAssistantServiceImpl::errorFrame);
    }

    private static Flux<ServerSentEvent<String>> errorFrame(Throwable ex) {
        String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "AI 服务处理失败，请稍后重试";
        return Flux.just(Sse.error(message));
    }

    @Override
    public Mono<WorkOrderCreated> submitWorkOrder(AiWorkOrderSubmitCommand command, String token) {
        if (!StringUtils.hasText(token)) {
            return Mono.error(new RequestException("登录已过期，请重新登录"));
        }
        if (command == null) {
            return Mono.error(new RequestException("请求内容不可为空"));
        }
        if (!StringUtils.hasText(command.getConversationId())) {
            return Mono.error(new RequestException("缺少会话标识"));
        }
        if (command.getUserSelect() == null) {
            return Mono.error(new RequestException("缺少用户选择"));
        }
        if (!StringUtils.hasText(command.getTitle())) {
            return Mono.error(new RequestException("工单标题不可为空"));
        }
        if (!StringUtils.hasText(command.getProblemDescription())) {
            return Mono.error(new RequestException("工单描述不可为空"));
        }

        WorkOrderCreateCommand create = new WorkOrderCreateCommand();
        create.setTitle(command.getTitle().trim());
        create.setType(command.getType());
        create.setPriority(command.getPriority());
        create.setProblemDescription(command.getProblemDescription().trim());
        create.setOriginalProblemDescription(command.getOriginalProblemDescription());
        create.setSolutionDetail(command.getSolutionDetail());
        // 归档信息由服务端从这次对话取，不信浏览器自报的两个值
        create.setAiConversationId(command.getConversationId());
        create.setAiSelectedOptionId(command.getUserSelect());

        return workOrderClient.create(token, create);
    }
}
