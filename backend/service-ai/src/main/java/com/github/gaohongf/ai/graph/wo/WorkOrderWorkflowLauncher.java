package com.github.gaohongf.ai.graph.wo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.github.gaohongf.ai.chat.sse.ChatEvent;
import com.github.gaohongf.ai.chat.sse.WorkflowInfo;
import com.github.gaohongf.ai.util.DateTimeTools;
import com.github.gaohongf.web.sse.SseElement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static com.alibaba.cloud.ai.graph.StateGraph.*;

@Component
public class WorkOrderWorkflowLauncher {

    public final static String GRAPH_NAME = "work-order-flow";
    public final static String DRAFT_GRAPH_NAME = "work-order-draft";
    private final CompiledGraph graph;
    private final CompiledGraph draftGraph;
    private static final Pattern INT = Pattern.compile("[+-]?\\d+");

    private final String systemPrompt = """
                你是一个运维工单系统的意图分析助手。
                你将帮助用户完成工单的填写。

                你不是工单执行者：你的职责只是把用户的输入理解成"他想要什么"，供他确认后生成工单，
                具体怎么处理由人来完成。所以你不必判断自己能不能做到，也不要说明自己的能力边界。

                每一次作答都分成前后两步，两步永远不能合并：
                第一步「判定」只回答"信息够不够"，不产出任何内容；
                第二步「作答」才产出内容，且形态由第一步决定 —— 信息不够时，这一轮的全部产出就是
                一句问话，而不是选项。
                先做完判定再开口，禁止一边判定一边就把选项给出去。

                用户自己给的推测和方案不可轻信，你需要分辨它们，然后作出自己的判断和建议，不要被用户带歪。
                如果用户的描述中带有模糊的时间，可使用时间工具来将用户的模糊时间尽力描述的更具体。
            """;

    public WorkOrderWorkflowLauncher(ChatClient chatClient, RedisSaver redisSaver, DateTimeTools dateTimeTools)
            throws GraphStateException {
        this.graph = new StateGraph(GRAPH_NAME, getKeyStrategyFactory())
                .addNode("extract", new ExtractNode(chatClient, systemPrompt))
                .addNode("validate", new ValidateNode())
                .addNode("ask", new AskNode(chatClient, systemPrompt))
                .addNode("options", new OptionsNode(chatClient, systemPrompt, dateTimeTools))
                .addNode("againExtract", new ExtractNode(chatClient, systemPrompt))
                .addEdge(START, "extract")
                .addEdge("extract", "validate")
                .addConditionalEdges("validate",
                        AsyncEdgeAction.edge_async((state) -> state.value("missing", List.class)
                                .map(List::isEmpty).map(isEmpty -> isEmpty ? "options" : "ask").orElse("options")),
                        Map.of("ask", "ask", "options", "options"))
                .addEdge("ask", "againExtract")
                .addConditionalEdges("options",
                        AsyncEdgeAction.edge_async((state) -> state.value("userInput", String.class).map(
                                s -> INT.matcher(s).matches() ? "selected" : "append").orElse("append")),
                        Map.of("selected", END, "append", "againExtract"))
                .addEdge("againExtract", "validate")
                .compile(CompileConfig.builder().saverConfig(SaverConfig.builder().register(redisSaver).build())
                        .interruptBefore("againExtract").build());

        // 生成工单是另一条独立的小流程：START → draft → END。
        //
        // 它刻意不接对话图的那个中断点，也不注册 checkpoint：
        // draft 需要的东西只有"哪段对话"和"选了第几项"，而对话内容在 ChatMemory 里
        // （同一个 conversationId）。把生成塞进对话图，就必须让"继续对话"和"生成"
        // 在共享的累积状态里分流 —— 那会把本来已经跑通的意图推断也一起拖进
        // 恢复语义里，改错一处第一步就给不出选项。
        this.draftGraph = new StateGraph(DRAFT_GRAPH_NAME, draftKeyStrategies())
                .addNode("draft", new DraftNode(chatClient, dateTimeTools))
                .addEdge(START, "draft")
                .addEdge("draft", END)
                .compile();
    }

    private static KeyStrategyFactory draftKeyStrategies() {
        Map<String, KeyStrategy> strategies = new HashMap<>();
        strategies.put("conversationId", new ReplaceStrategy());
        strategies.put("userSelect", new ReplaceStrategy());
        strategies.put("workOrderDraft", new ReplaceStrategy());
        return () -> strategies;
    }

    private KeyStrategyFactory getKeyStrategyFactory() {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("draft", new ReplaceStrategy());
            strategies.put("missing", new ReplaceStrategy());
            strategies.put("messages", new AppendStrategy());
            strategies.put("conversationId", new ReplaceStrategy());
            strategies.put("userInput", new ReplaceStrategy());
            strategies.put("options", new ReplaceStrategy());
            return strategies;
        };
    }

    public Flux<SseElement> stream(String threadId, String userInput) {
        if (threadId == null) {
            threadId = "wo-" + UUID.randomUUID().toString().replace("-", "");
        }
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        // conversationId 必须和 threadId 一致：它是各节点写 ChatMemory 的 key，
        // 也是客户端拿到的那个 id —— 后续生成工单用同一个 id 才能读到这段对话。
        // 不传的话 ExtractNode 会自己生成一个 UUID，那段记忆就再也没有人能读到了。
        return toSse(graph.stream(
                Map.of("userInput", userInput, "conversationId", threadId), config),
                threadId);
    }

    /**
     * 用户选中了 {@code userSelect} 指向的选项，生成工单。
     *
     * <p>
     * 走 {@code draftGraph}，不碰对话图的 checkpoint：这里需要的上下文全部在
     * ChatMemory 里（key 就是 threadId 对应的 conversationId），对话状态怎么累积、
     * 中断点停在哪，都不影响这次生成。{@code threadId} 在这里只用于日志和 SSE 的 workflow 事件。
     */
    public Flux<SseElement> select(String threadId, Integer userSelect) {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        return toSse(draftGraph.stream(
                Map.of("conversationId", threadId, "userSelect", userSelect), config),
                threadId);
    }

    private static Flux<SseElement> toSse(Flux<NodeOutput> outputs, String threadId) {
        return outputs
                .filter(output -> output instanceof StreamingOutput)
                .cast(StreamingOutput.class)
                // draft 是节点借一条单元素 Flux 带出来的工单对象，和 ask/options 走同一条通道
                .filter(output -> "ask".equals(output.node())
                        || "options".equals(output.node())
                        || "draft".equals(output.node()))
                .map(output -> new ChatEvent(output.node(), output.getOriginData()))
                .subscribeOn(Schedulers.boundedElastic())
                .cast(SseElement.class)
                .startWith(Mono.just(new WorkflowInfo(threadId)));
    }
}
