package com.github.gaohongf.ai.service.impl;

import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.github.gaohongf.ai.service.AiAssistantService;
import com.github.gaohongf.ai.util.Sse;
import com.github.gaohongf.ai.util.SsePayload;
import com.lingyun.base.rsm.exception.RequestException;

import cn.dev33.satoken.stp.StpLogic;
import lombok.AllArgsConstructor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@AllArgsConstructor
@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private final ChatClient chatClient;
    private final OpenAiChatProperties openAiChatProperties;

    @Override
    public Flux<ServerSentEvent<String>> workOrderUserInputIntentInference(String id, String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return Flux.error(() -> new RequestException("用户输入不可为空"));
        }

        String systemPrompt = """
                    你是一个运维工单系统的意图分析助手。
                    用户可能提交了故障描述或一些需求，但往往描述不清。

                    你不是执行者：你的职责只是把用户的输入理解成"他想要什么"，供他确认后生成工单，
                    具体怎么处理由人来完成。所以你不必判断自己能不能做到，也不要说明自己的能力边界。

                    你的任务是：
                    1. 区分用户输入是需求还是故障，分析用户的意图。
                    2. 推测 3-5 个具体的用户意图作为选项，供用户选择确认，可能性是一个1-100的整数。
                    3. 用户自己给的推测和方案不可轻信，你需要分辨它们，然后作出自己的判断和建议，不要被用户带歪。
                    4. 每个选项只描述"用户想要什么"，不要写你自己的回应。严禁出现"我无法""我不能"
                       "请提供更多信息""建议联系供应商"这类能力声明、免责说明或指路建议 —— 它们不是意图。
                    5. 需要现场或人工执行的诉求同样是合理的运维意图（例如在机房布放网线、更换硬件、
                       现场巡检），照常作为选项列出，由工单流程派给相应的人去执行，不要因为自己做不到就排除它。
                    6. 如果用户的输入与运维无关（例如闲聊、其他业务领域的问题），仍然给出选项，
                       但在 title/content 里如实说明这属于"非运维诉求"，可能性给低分。
                    7. 如果用户再次补充了内容，请再给出其他可能的推测，如果补充的内容和之前的内容一致，请给出相同的推测，如果不一致，请给出新的推测。

                    字段含义：title 是这条意图的简短标题（不超过 15 字）；
                    content 是它的具体内涵，说明用户想达成什么、涉及哪个系统或对象。

                    必须以纯 JSONL 格式返回，每行一个 JSON 对象，行内不要出现真实的换行符，
                    不要包含任何 Markdown 标记与其他无关文本，格式如下：
                    {"id": 1, "title": "标题1", "content": "用户可能想说的内容1", "probability": "80"}
                    {"id": 2, "title": "标题2", "content": "用户可能想说的内容2", "probability": "50"}
                    {"id": 3, "title": "标题3", "content": "用户可能想说的内容3", "probability": "10"}
                """;
        String modelName = openAiChatProperties.getOptions().getModel();
        String conversationId = id != null ? id : UUID.randomUUID().toString();
        return Flux.<ServerSentEvent<String>>create((sink) -> {
            // 流式返回
            StringBuilder builder = new StringBuilder();
            Disposable disposable = chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userInput)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content()
                    .subscribe((token) -> {
                        // 控制每次返回的token，如果包含换行符，则发送一个delta消息，并清空builder
                        // 每次将返回一条完整一行而非碎片的token
                        int idx = -1;
                        builder.append(token);
                        while ((idx = builder.indexOf("\n")) != -1) {
                            sink.next(Sse.delta(builder.substring(0, idx + 1)));
                            builder.replace(0, idx + 1, "");
                            idx = -1;
                        }
                    }, (ex) -> {
                        // 报错时发送一个错误消息
                        sink.next(Sse.error(ex.getMessage()));
                        // 必须调用complete否则无法结束当前流
                        sink.complete();
                    }, () -> {
                        // 查看是否有剩余的token，如果有则发送一个delta消息
                        if (!builder.isEmpty()) {
                            sink.next(Sse.delta(builder.toString()));
                        }
                        sink.next(Sse.done(Sse.DONE_REASON_SUCCESS));
                        // 必须调用complete表示结束
                        sink.complete();
                    });
            sink.onDispose(disposable);
        }).startWith(Flux
                .just(Sse.start(new SsePayload(conversationId, modelName, System.currentTimeMillis()))));
    }

    @Override
    public Flux<ServerSentEvent<String>> workOrderSubmit(String id, Integer userSelect) {
        if (userSelect == null) {
            return Flux.error(() -> new RequestException("用户输入不可为空"));
        }

        String systemPrompt = """
                        用户已经在之前的对话里选择了一个意图，现在需要你协助把它整理成一张工单。

                        你的任务是：
                        1. 结合用户最初的问题描述和后续的补充，给出工单标题、类型、优先级、描述和解决方法。
                        2. 描述控制在 80-200 字；解决方法要详细、可执行、分步骤。
                        3. 不要被用户带歪：用户自己给的判断和建议只作参考，最终以你的专业判断为准。
                        4. 如果用户选择的意图与他最初的描述明显矛盾，以用户选择的那个为准。
                        5. 解决方法面向**执行这张工单的人**来写（可能是现场人员或供应商），只写该怎么做；
                           不要写"我无法…""请联系…"这类能力声明或免责说明，需要现场操作的步骤照常写出来。

                        必须以纯 JSONL 格式返回，每行一个 JSON 对象，行内不要出现真实的换行符，
                        不要包含任何 Markdown 标记与其他无关文本，顺序如下：
                        {"title": "工单标题"}
                        {"type": "demand"}
                        {"priority": "medium"}
                        {"content": "工单描述"}
                        {"solution_detail": "工单解决方法详细描述"}
                        {"user_choose": 2}
                        {"user_input_0": "用户第一轮说过的话"}
                        {"user_input_1": "用户第二轮说过的话"}

                        字段取值要求：
                        - type 只能是 demand（需求）或 fault（故障）。
                        - priority 只能是 low、medium、high、urgent 之一。
                        - user_choose 是用户所选的选项编号。
                        - user_input_N 是用户在对话里说过的原话，一轮一行，N 从 0 开始递增：
                          用户补充过几次就输出几行，逐字保留用户的原始措辞（错别字也保留），
                          不要改写、不要总结、不要合并成一行；用户没有补充过就只输出 user_input_0 这一行。
                    """;
        String modelName = openAiChatProperties.getOptions().getModel();
        return Flux.<ServerSentEvent<String>>create((sink) -> {
            // 流式返回
            StringBuilder builder = new StringBuilder();
            Disposable disposable = chatClient
                    .prompt().system(systemPrompt)
                    .user("用户选择：" + userSelect)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))
                    .stream()
                    .content()
                    .subscribe((token) -> {
                        // 控制每次返回的token，如果包含换行符，则发送一个delta消息，并清空builder
                        // 每次将返回一条完整一行而非碎片的token
                        int idx = -1;
                        builder.append(token);
                        while ((idx = builder.indexOf("\n")) != -1) {
                            sink.next(Sse.delta(builder.substring(0, idx + 1)));
                            builder.replace(0, idx + 1, "");
                            idx = -1;
                        }
                    }, (ex) -> {
                        // 报错时发送一个错误消息
                        sink.next(Sse.error(ex.getMessage()));
                        // 必须调用complete否则无法结束当前流
                        sink.complete();
                    }, () -> {
                        // 查看是否有剩余的token，如果有则发送一个delta消息
                        if (!builder.isEmpty()) {
                            sink.next(Sse.delta(builder.toString()));
                        }
                        sink.next(Sse.done(Sse.DONE_REASON_SUCCESS));
                        // 必须调用complete表示结束
                        sink.complete();
                    });
            sink.onDispose(disposable);
        }).startWith(Flux
                .just(Sse.start(new SsePayload(id, modelName, System.currentTimeMillis()))));
    }

}
