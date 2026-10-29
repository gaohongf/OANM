package com.github.gaohongf.ai.graph.wo.v2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.github.gaohongf.ai.client.FileClient;
import com.github.gaohongf.ai.entity.dto.ImageCheck;

import lombok.extern.slf4j.Slf4j;

/**
 * 识图：把本轮图片交给多模态模型，筛掉与问题无关的图，留下能写进工单的现场图片。
 *
 * <h2>为什么一张图问一次模型</h2>
 * 结论必须能对应回具体的图片地址：过滤掉哪几个链接、保留哪几个链接，是后续建单时
 * 要用的事实。多张图一起问只能得到一段混在一起的描述，模型无法可靠地说清
 * "第几张图没用"，而图片顺序与 URL 顺序之间没有任何可校验的绑定。
 *
 * <h2>结果怎么用</h2>
 * <ul>
 *   <li>{@code vision_res}：有效图片的文字描述，给追问/提取节点读，只留在后端；</li>
 *   <li>{@code workOrderImages}：有效图片的地址，按会话累积，建单时随工单写入。</li>
 * </ul>
 * 被过滤掉的图片也会被静默忽略 —— 它们是表情包或与问题无关的内容，
 * 不应该出现在工单里，也没有必要让用户为此多做一步。
 */
@Slf4j
public class VisionNode extends AINode {

    private static final String SYSTEM_PROMPT = """
            你是运维工单系统的图片筛选助手。
            你只判断图片对记录本次运维问题是否有用，不要回答用户的问题，
            也不要给出处理建议 —— 那些由后面的环节负责。
            """;

    private static final String USER_PROMPT = """
            用户正在描述一个运维问题，并上传了一张图片。
            请判断这张图片对记录这个问题是否有用。

            保留（relevant = true）：
            - 出现问题的设备、现场环境、接线、指示灯、报错屏幕、软件界面截图；
            - 用户用来补充说明问题的示意图。

            过滤（relevant = false）：
            - 表情包、梗图、卡通贴纸；
            - 与本次问题无关的生活照、风景、宠物、自拍；
            - 与问题无关的聊天截图、游戏截图、广告；
            - 模糊到看不清内容、且与问题无关的图片；
            - 政治敏感、暴力血腥、涉黄、涉及未成年人迫害的图片。

            判断时结合用户这句话：
            {userInput}

            保留时，description 用一两句话描述图中与问题有关的内容
            （设备、屏幕上的文字、指示灯状态等），不要写“图片内容描述：”之类的前缀。
            过滤时，description 用一句话说明这张图实际是什么，例如“表情包”。
            如果拿不准，但看起来可能和设备或系统故障有关，就保留。
            """;

    private final FileClient fileClient;

    /**
     * 走 {@code call().content()} 再手动解析，而不是 {@code entity()}：
     * 多模态消息里的图片必须先原样送到模型，结果解析只是第二步；
     * 这样也方便在解析失败时把原始响应记进日志。
     */
    private final BeanOutputConverter<ImageCheck> converter = new BeanOutputConverter<>(ImageCheck.class);

    public VisionNode(ChatClient chatClient, FileClient fileClient) {
        super(chatClient);
        this.systemPrompt = SYSTEM_PROMPT;
        this.userPromptTemplate = USER_PROMPT;
        this.fileClient = fileClient;
    }

    @Override
    protected Map<String, Object> execute(OverAllState state) {
        List<String> images = state.value("images", List.class)
                .map(items -> items.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList())
                .orElse(List.of());
        String userInput = state.value("userInput", String.class).orElse("");

        // 之前轮次已经确认过的有效图片。按会话累积，同一张图重复上传也只留一份。
        Set<String> known = new HashSet<>(state.value("workOrderImages", List.class)
                .map(items -> items.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList())
                .orElse(List.of()));

        List<String> descriptions = new ArrayList<>();
        List<String> validImages = new ArrayList<>();
        for (String url : images) {
            ImageCheck check = check(url, userInput);
            if (check == null || !Boolean.TRUE.equals(check.getRelevant())) {
                continue;
            }
            String description = check.getDescription();
            if (StringUtils.hasText(description)) {
                descriptions.add(description.trim());
            }
            if (known.add(url)) {
                validImages.add(url);
            }
        }

        return Map.of(
                // 空列表而不是 null：路由要靠它判断"这一轮是否还有图"，也避免 checkpoint 序列化 null
                "images", new ArrayList<>(),
                "vision_res", String.join("\n", descriptions),
                // AppendStrategy 累加到会话状态；这里只交出本轮新增的有效图片
                "workOrderImages", validImages);
    }

    /**
     * 单张图的判定。任何一张图读不到或模型调用失败都只跳过这一张 ——
     * 一张坏图不该让整轮识图失败，后面的追问和工单生成仍然可以继续。
     */
    private ImageCheck check(String url, String userInput) {
        try {
            FileClient.FileContent file = fileClient.load(url);
            Media media = Media.builder()
                    .mimeType(MimeType.valueOf(file.contentType()))
                    .data(file.bytes())
                    .build();
            UserMessage message = UserMessage.builder()
                    .text(renderUserPrompt(Map.of("userInput",
                            StringUtils.hasText(userInput) ? userInput.trim() : "（用户这一轮没有补充文字）"))
                            + "\n\n" + converter.getFormat())
                    .media(media)
                    .build();
            // 用 call 而不是 stream：判定结果只在后端给后续节点用，不需要逐字给用户看
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(message)
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                return null;
            }
            return converter.convert(content);
        } catch (RuntimeException ex) {
            log.warn("识图失败，按无关图片跳过: url={}, reason={}", url, ex.getMessage());
            return null;
        }
    }
}
