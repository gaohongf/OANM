package com.github.gaohongf.ai.graph.wo.v1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.github.gaohongf.ai.client.FileClient;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class VisionNode implements AsyncNodeAction {
    private final ChatClient chatClient;

    private final String systemPrompt;

    private final FileClient fileClient;

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return AsyncNodeAction.node_async((s) -> {
            Map<String, Object> map = new HashMap<>();

            List<Media> imgs = s.value("images", List.class).map(
                    images -> images.stream().map(i -> {
                        // 相对地址只有内网能访问, 必须取回字节让 Spring AI 内联给模型
                        FileClient.FileContent file = fileClient.load(i.toString());
                        return Media.builder()
                                .mimeType(MimeType.valueOf(file.contentType()))
                                .data(file.bytes())
                                .build();
                    }).toList())
                    .orElse(List.of());
            UserMessage message = UserMessage.builder().text("""
                    用户上传了图片
                    本节点你的任务是：
                    - 只描述图片的内容，不要在输出里说本轮干了什么事情，也不用在开头补充诸如“图片内容描述:”,“图片识别结果：”等注释。
                    - 无需也不要试图回复用户。
                    - 不要在识图结果里说本轮的识别规则。
                    - 如果图片过于模糊，不要猜测，直接输出：图片模糊，无法识别

                    如果图片符合以下特征，请直接返回：图片违规 【原因】
                    - 政治敏感
                    - 暴力血腥
                    - 涉黄
                    - 涉及未成年人迫害

                    如果没有图片就返回：未上传图片
                    """).media(imgs).build();

            // 用空列表而不是 null: 路由要靠它判断"这一轮是否还有图", 而且状态会被
            // checkpoint 序列化, null 值在合并/序列化两处都没有必要冒险。
            map.put("images", new ArrayList<>());
            // 用 call 而不是 stream: 识图结果只在后端给后续节点用, 不再作为流输出；
            // 存成完整字符串, AskNode 才能直接放进提示词（Flux 落进状态是分片）。
            map.put("vision_res", chatClient.prompt()
                    .system(systemPrompt)
                    .messages(message)
                    .call()
                    .content());
            return map;
        }).apply(state);
    }

}
