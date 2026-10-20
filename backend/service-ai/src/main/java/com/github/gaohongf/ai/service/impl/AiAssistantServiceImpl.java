package com.github.gaohongf.ai.service.impl;

import java.util.ArrayList;
import java.util.List;

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

    /** 与前端的上限一致。服务端仍然要挡, 不能只靠界面禁用。 */
    private static final int MAX_IMAGES = 3;

    /** 相对地址（/file/yyyy/MM/dd/xxx.jpg）远小于这个长度, 这里只防异常长的 query。 */
    private static final int MAX_IMAGE_URL_LENGTH = 512;

    private final OpenAiChatProperties openAiChatProperties;
    private final WorkOrderWorkflowLauncher workOrderWorkflowLauncher;
    private final WorkOrderClient workOrderClient;

    @Override
    public Flux<ServerSentEvent<String>> workOrderUserInputIntentInference(
            String id, String userInput, List<String> images) {
        if (!StringUtils.hasText(userInput)) {
            return errorFrame(new RequestException("用户输入不可为空"));
        }
        List<String> imageUrls;
        try {
            imageUrls = normalizeImages(images);
        } catch (RequestException ex) {
            return errorFrame(ex);
        }
        return intentStream(workOrderWorkflowLauncher.stream(id, userInput, imageUrls));
    }

    /**
     * 校验并归一化图片地址。
     *
     * <p>
     * 只接受上传接口返回的相对地址（{@code /file/...}）：这些值会被 service-ai 拿去
     * 向 service-files 取字节，放任意字符串（尤其是绝对地址）进去等于给自己留了一个
     * SSRF 入口。空列表是正常情况（本轮没附图），不报错。
     */
    private static List<String> normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return new ArrayList<>();
        }
        if (images.size() > MAX_IMAGES) {
            throw new RequestException("最多只能附带 " + MAX_IMAGES + " 张图片");
        }
        List<String> normalized = new ArrayList<>(images.size());
        for (String image : images) {
            if (!StringUtils.hasText(image)) {
                throw new RequestException("图片地址不可为空");
            }
            String url = image.trim();
            if (url.length() > MAX_IMAGE_URL_LENGTH) {
                throw new RequestException("图片地址过长");
            }
            // 只认本服务的相对路径。绝对地址一律拒绝：FileClient 用 baseUrl + 该路径
            // 发请求, 放行 http(s) 就等于允许调用方指定任意内网目标。
            if (!url.startsWith("/file/") || url.contains("..")) {
                throw new RequestException("图片地址不合法");
            }
            normalized.add(url);
        }
        // 返回可变的 ArrayList：它会被写进工作流状态并随 checkpoint 落 Redis。
        // List.of()/List.copyOf() 的不可变实现属于 JDK 内部类，交给序列化器是没必要的风险。
        return normalized;
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
