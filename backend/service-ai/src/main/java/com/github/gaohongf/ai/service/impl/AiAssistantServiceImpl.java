package com.github.gaohongf.ai.service.impl;

import java.util.Properties;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.github.gaohongf.ai.service.AiAssistantService;
import com.github.gaohongf.ai.util.Sse;
import com.github.gaohongf.ai.util.SsePayload;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private final ChatClient chatClient;
    private final OpenAiChatProperties openAiChatProperties;
    private final static String systemPrompt = """
                你是一个资深的运维工程师。
                用户提交了故障描述和一些需求，但往往描述不清。

                你的任务是：
                1. 区分用户输入是需求还是故障
                2. 推测 3-5 个具体的故障现象或者明确需求作为选项，供用户选择确认，可能性是一个1-100的整数。
                3. 用户自己给的推测和方案不可轻信，你需要分辨它们，然后作出自己的判断和建议，不要被用户带歪。
                4. 明确拒绝用户的无关要求，并且按照同样的限定格式输出自己能做什么。

                第一行要输出用户的输入是需求还是故障，如果是需求则输出"demand",如果是故障则输出"fault",换行后接下来，必须以纯 JSONL 格式返回，不要包含任何 Markdown 标记与其他无关文本，格式如下：
                fault
                {"title": "推测的故障1", "content": "具体故障描述1", "probability": "80"}
                {"title": "推测的故障2", "content": "具体故障描述2", "probability": "50"}
                {"title": "推测的故障3", "content": "具体故障描述3", "probability": "10"}
            """;

    public AiAssistantServiceImpl(ChatClient.Builder chatClientBuilder, OpenAiChatProperties openAiChatProperties) {
        this.chatClient = chatClientBuilder.build();
        this.openAiChatProperties = openAiChatProperties;
    }

    @Override
    public Flux<ServerSentEvent<String>> workOrderUserInputIntentInference(String userInput) {
        String modelName = openAiChatProperties.getOptions().getModel();
        return Flux.<ServerSentEvent<String>>create((sink) -> {
            StringBuilder builder = new StringBuilder();
            Disposable disposable = chatClient.prompt().system(systemPrompt)
                    .user(userInput)
                    .stream()
                    .content()
                    .subscribe((token) -> {
                        int idx = -1;
                        builder.append(token);
                        while ((idx = builder.indexOf("\n")) != -1) {
                            sink.next(Sse.delta(builder.substring(0, idx + 1)));
                            builder.replace(0, idx + 1, "");
                            idx = -1;
                        }
                    }, (ex) -> {
                        sink.next(Sse.error(ex.getMessage()));
                        sink.complete();
                    }, () -> {
                        if (!builder.isEmpty()) {
                            sink.next(Sse.delta(builder.toString()));
                        }
                        sink.next(Sse.done(Sse.DONE_REASON_SUCCESS));
                        sink.complete();
                    });
            sink.onDispose(disposable);
        }).startWith(Flux
                .just(Sse.start(new SsePayload(UUID.randomUUID().toString(), modelName, System.currentTimeMillis()))));
    }

}
