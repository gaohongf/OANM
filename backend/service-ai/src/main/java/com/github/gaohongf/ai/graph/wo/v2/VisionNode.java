package com.github.gaohongf.ai.graph.wo.v2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.client.FileClient;

/** 把本轮图片交给多模态模型，得到一段只供后端使用的文字描述。 */
public class VisionNode extends AINode {

    private static final String USER_PROMPT = """
            用户上传了图片。
            本节点你的任务是：
            - 只描述图片的内容，不要在输出里说本轮干了什么事情，也不用在开头补充诸如“图片内容描述:”“图片识别结果：”等注释。
            - 无需也不要试图回复用户。
            - 不要在识图结果里说本轮的识别规则。
            - 如果图片过于模糊，不要猜测，直接输出：图片模糊，无法识别。

            如果图片符合以下特征，请直接返回：图片违规 【原因】
            - 政治敏感
            - 暴力血腥
            - 涉黄
            - 涉及未成年人迫害

            如果没有图片就返回：未上传图片
            """;

    private final FileClient fileClient;

    public VisionNode(ChatClient chatClient, String systemPrompt, FileClient fileClient) {
        super(chatClient);
        this.systemPrompt = systemPrompt;
        this.fileClient = fileClient;
    }

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        Map<String, Object> output = new HashMap<>();

        List<Media> images = state.value("images", List.class)
                .map(items -> items.stream().map(item -> {
                    // 相对地址只有内网能访问，必须取回字节让 Spring AI 内联给模型
                    FileClient.FileContent file = fileClient.load(item.toString());
                    return Media.builder()
                            .mimeType(MimeType.valueOf(file.contentType()))
                            .data(file.bytes())
                            .build();
                }).toList())
                .orElse(List.of());

        UserMessage message = UserMessage.builder().text(USER_PROMPT).media(images).build();

        // 空列表而不是 null：路由要靠它判断"这一轮是否还有图"，也避免 checkpoint 序列化 null
        output.put("images", new ArrayList<>());
        // 用 call 而不是 stream：识图结果只在后端给后续节点用，存成完整字符串才能放进提示词
        output.put("vision_res", chatClient.prompt()
                .system(systemPrompt)
                .messages(message)
                .call()
                .content());
        return output;
    }
}
