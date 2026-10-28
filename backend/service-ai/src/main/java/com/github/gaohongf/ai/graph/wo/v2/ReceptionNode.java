package com.github.gaohongf.ai.graph.wo.v2;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.graph.OverAllState;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 接待：接住用户的第一句话。
 *
 * <h2>为什么要单独占一个节点</h2>
 * 用户说完第一句，后面还要经过识图、提取、检索、确认/追问，才轮到真正的答复；
 * 这段时间里页面是空的，情绪急的用户会以为没人理。这一节点只负责先回一句人话：
 * 语气急、低落就先安抚，平静就表示已经收到、复述要点。
 *
 * <p>
 * 它只跑一次：{@code greeted} 由本节点置真，后续轮次再也不会补一句"收到"。
 * 它也不改变本轮形态 —— 后面该追问追问、该给方案给方案，这句只是开场白。
 */
@Slf4j
public class ReceptionNode extends AIStreamOutputNode<String> {

    private static final String SYSTEM_PROMPT = """
            你是运维工单系统的接待助手。
            用户刚发来第一条问题描述，你只负责接住这句话，让用户知道有人在处理；
            具体的诊断、追问和解决步骤都由后面的环节负责。

            语气：
            - 温和、松弛，像熟悉的同事在聊天；不要客服腔（"您好""亲""很高兴为您服务"），
              也不要套模板：别写成"别急，……我看到了，我来帮你理一下"这种三段式。
            - 可以用"~"让语气软下来，例如"不要着急~，打印机坏了的事我先帮你理一下"。
            - 允许用颜文字制造轻松的气氛，例如"不要着急~，打印机坏了的事我先帮你理一下 (｡･ω･｡)"。
              最多一个、放在句末，不是每句都要有；不要用 emoji 表情符号。
            - 用户明显着急、沮丧时，优先用温和的话，不要为了可爱硬塞颜文字。

            规则：
            1. 只输出一句话，四十字左右；不要引号、前缀、Markdown，也不要换行。
            2. 先看用户的语气：
               - 着急、焦虑、沮丧、抱怨或催促：先安抚，再点出你听到的问题。
                 用"不要着急~""慢慢来""别慌，我们一起看看"这类说法，
                 不要用"请冷静""别激动"这种命令式的词。
               - 平静陈述：轻松地表示已经收到，并复述问题要点，
                 例如"收到~，你说的打印机出不了纸，我先看看有没有现成的处理办法。"
            3. 只复述用户已经说过的事，不要编造设备、地点、时间；
               不要给解决步骤，也不要问时间地点。
            4. 不要提"知识库""AI""助手""工单系统"这类系统称呼，也不要自报身份。
            """;

    private static final String USER_PROMPT = """
            用户的第一句话：
            {userInput}
            """;

    public ReceptionNode(ChatClient chatClient) {
        super(chatClient, "messages");
        this.systemPrompt = SYSTEM_PROMPT;
        this.userPromptTemplate = USER_PROMPT;
    }

    @Override
    public Flux<String> stream(OverAllState state) {
        String userInput = state.value("userInput", String.class).orElse("");
        if (!StringUtils.hasText(userInput)) {
            return Flux.empty();
        }
        return prompt(Map.of("userInput", userInput.trim()))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(state)))
                .stream()
                .content()
                // 接待只是开场，失败不能把这一轮卡死：后面的追问/方案照常继续
                .onErrorResume(ex -> {
                    log.warn("接待语生成失败，跳过: {}", ex.getMessage());
                    return Flux.empty();
                });
    }

    @Override
    protected Map<String, Object> extraState(OverAllState state) {
        // 只接待一次：哪怕模型一个字都没吐出来，后续轮次也不再补一句"收到"
        return Map.of("greeted", true);
    }
}
