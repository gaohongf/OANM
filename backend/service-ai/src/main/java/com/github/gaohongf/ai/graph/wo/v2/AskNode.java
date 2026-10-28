package com.github.gaohongf.ai.graph.wo.v2;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import com.alibaba.cloud.ai.graph.OverAllState;

import reactor.core.publisher.Flux;

/**
 * 关键信息不全时的追问。
 *
 * <p>
 * 输出的是 1-3 句短句，用换行分隔：情绪安抚、时间、地点各占一句，
 * 前端已经按 {@code pre-wrap} 渲染，用户看到的是一条自然的多行回复，而不是一句长话。
 */
public class AskNode extends AIStreamOutputNode<String> {

    private static final String USER_PROMPT = """
            用户之前的描述缺少了一些关键信息。

            你在当前节点的任务是:
            1. 用日常说话的语气把缺的那一样问清楚，可以顺带给一两个例子帮用户回答。
               （时间如"昨天上午"，地点如"行政楼行政办公室"）。
            2. 如果用户语气低落、急切，先用一句短话安抚，再继续问；不要生硬，不要阴阳怪气，不要偏离主题。
            3. 输出 1-3 句短句，每句一行（用换行分隔）。情绪安抚单独一句，追问各占一句。
               不要把多件事挤进一个长句，也不要空行、编号或列表符号。
            4. 一次只问一样；两样都缺就分两句问，不要挤成一句。
            5. 如果缺少多样信息，询问和举例时要自然地组合进问题语句中，避免机械式对话。
            6. 已经答过的那样不要再问，用户这一轮补上的信息要认账。
            7. 提问要根据之前的已有信息自然追问，曾经举过例子了就不需要再举了。
            8. 你当前节点无需结构化输出。
            9. 如果下面给出了图片内容描述（非空），说明用户这一轮发了图片。可以自然地提到
               "我看到了你发的图片"，并依据图里与故障相关的信息（报错文字、界面状态、设备等）
               追问缺失的内容。但不要逐字复述图片描述，也不要把图里的文字、代码、表格整段
               转述出来。如果图片是无关运维的内容就忽略掉。图片描述为空时，不要凭空提到图片。
            10. 如果用户的输入与运维工作无关，则提醒用户，并将对话引向运维相关内容。

            缺少的关键信息如下:
            {missing}

            这一轮图片的内容描述（没有图片时为空）:
            {vision}
            """;

    public AskNode(ChatClient chatClient, String systemPrompt) {
        super(chatClient, "messages");
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = USER_PROMPT;
    }

    @Override
    public Flux<String> stream(OverAllState state) {
        String missing = formatMissing(state);
        String vision = state.value("vision_res", String.class).orElse("");
        return prompt(Map.of("missing", missing, "vision", vision))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(state)))
                .stream()
                .content();
    }

    private static String formatMissing(OverAllState state) {
        List<?> missing = state.value("missing", List.class).orElse(List.of());
        return missing.stream().map(String::valueOf).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
