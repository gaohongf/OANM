package com.github.gaohongf.ai.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.github.gaohongf.ai.entity.req.AiWorkOrderSubmitCommand;
import com.github.gaohongf.ai.service.AiAssistantService;
import com.github.gaohongf.auth.annotation.IsOpen;
import com.github.gaohongf.auth.annotation.LoginOnly;
import com.github.gaohongf.web.rsm.ReactiveRsmWriter;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.message.Response;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@RequestMapping("/api/ai/assistant")
@RestController
public class AiAssistantController {

    /** 与前端 http.ts / sse.ts 发送的登录头一致 */
    private static final String TOKEN_HEADER = "satoken";

    private final AiAssistantService aiAssistantService;
    private final ReactiveRsmWriter rsmWriter;

    /**
     * 意图推断 / 工单生成 / 自助解决决策。
     *
     * <p>
     * 三个动作共用同一个流接口，用参数区分：
     * <ul>
     *   <li>带 {@code userSelect}：用户选中了工单意图，生成草稿；</li>
     *   <li>带 {@code action}：用户点了自助建议之后的两张决策卡片；</li>
     *   <li>只带 {@code userInput}：继续这段对话。</li>
     * </ul>
     *
     * <p>
     * {@code images} 是本轮用户附带的图片访问地址（逗号分隔或重复参数）。
     * 识图结果只留在后端，用于追问和提取。
     *
     * <p>
     * {@code @IsOpen} 保持不变：意图推断是对话入口，生成也只是接着这段对话往下走一步；
     * 真正需要授权的动作是提交建单，见下面的 {@code /wo/submit}。
     */
    @IsOpen
    @GetMapping(value = "/wo/uiii", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> workOrderUserInputIntentInference(
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "userInput", required = false) String userInput,
            @RequestParam(value = "userSelect", required = false) Integer userSelect,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "images", required = false) List<String> images) {
        if (action != null) {
            return aiAssistantService.workOrderDecision(id, action);
        }
        if (userSelect != null) {
            return aiAssistantService.workOrderDraftGenerate(id, userSelect);
        }
        return aiAssistantService.workOrderUserInputIntentInference(id, userInput, images);
    }

    /**
     * 用户确认草稿后提交建单。
     *
     * <p>
     * {@code @LoginOnly}：登录即可，不额外要求一条 AI 权限 —— 真正的门禁是工单服务的
     * {@code POST:/api/ops/work_order}，token 原样透传过去由它校验。自注册扫描器会跳过
     * {@code @LoginOnly}，所以这个端点不会在权限管理里留下一行。
     *
     * <p>
     * 返回 {@code Mono<Response>} 并在 map 里手工包信封：WebFlux 没有 servlet 那个
     * {@code ResponseBodyAdvice}，普通对象不会被自动包成 RSM 响应
     * （理由见 {@link ReactiveRsmWriter} 的类注释）。
     */
    @LoginOnly
    @PostMapping("/wo/submit")
    public Mono<Response> submitWorkOrder(ServerWebExchange exchange,
            @RequestBody AiWorkOrderSubmitCommand command) {
        String token = exchange.getRequest().getHeaders().getFirst(TOKEN_HEADER);
        return aiAssistantService.submitWorkOrder(command, token)
                .map(created -> rsmWriter.success(exchange, GenericRsm.CREATE_SUCCESS, created));
    }
}
