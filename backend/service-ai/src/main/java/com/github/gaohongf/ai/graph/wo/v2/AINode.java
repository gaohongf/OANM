package com.github.gaohongf.ai.graph.wo.v2;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

import com.alibaba.cloud.ai.graph.OverAllState;

/**
 * 需要调用大模型的节点。
 *
 * <p>
 * 只保留所有 AI 节点都会用到的东西：客户端、系统提示词、用户提示词模板，
 * 以及"用这次状态渲染用户提示词"这个动作。具体是流式输出还是结构化输出，
 * 由 {@link AIStreamOutputNode} 和各自实现 {@code execute} 的节点决定。
 */
public abstract class AINode extends WorkflowNode {

    protected final ChatClient chatClient;

    protected String systemPrompt;
    protected String userPromptTemplate;

    protected AINode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public void setUserPromptTemplate(String userPromptTemplate) {
        this.userPromptTemplate = userPromptTemplate;
    }

    public List<Object> getTools() {
        return List.of();
    }

    protected PromptTemplate getUserPromptTemplate() {
        return PromptTemplate.builder().template(userPromptTemplate).build();
    }

    /** 用给定的变量渲染用户提示词。变量值必须非 null（调用方需要自己兜底）。 */
    protected String renderUserPrompt(Map<String, Object> variables) {
        return getUserPromptTemplate().mutate().variables(variables).build().render();
    }

    protected ChatClient.ChatClientRequestSpec prompt(Map<String, Object> variables) {
        return chatClient.prompt().system(systemPrompt).user(renderUserPrompt(variables));
    }

    protected String conversationId(OverAllState state) {
        return state.value("conversationId", String.class).orElse(null);
    }
}
