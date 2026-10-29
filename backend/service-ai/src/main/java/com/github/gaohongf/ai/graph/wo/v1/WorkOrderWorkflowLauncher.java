package com.github.gaohongf.ai.graph.wo.v1;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
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
import com.github.gaohongf.ai.client.FileClient;
import com.github.gaohongf.ai.graph.wo.IWorkOrderWorkflowRunner;
import com.github.gaohongf.ai.util.DateTimeTools;
import com.github.gaohongf.web.sse.SseElement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static com.alibaba.cloud.ai.graph.StateGraph.*;

/**
 * v1 工作流，保留作为旧流程的对照实现。
 *
 * <p>
 * 刻意不再标注 {@code @Component}：v2 的启动器才是实际注入的
 * {@link IWorkOrderWorkflowRunner}。两个都注册会让 Spring 在注入点遇到两个候选而启动失败。
 */
public class WorkOrderWorkflowLauncher implements IWorkOrderWorkflowRunner {

        public final static String GRAPH_NAME = "work-order-flow";
        public final static String DRAFT_GRAPH_NAME = "work-order-draft";
        private final CompiledGraph graph;
        private final CompiledGraph draftGraph;
        private static final Pattern INT = Pattern.compile("[+-]?\\d+");
        private final ChatClient chatClient;

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

        /** 护栏拦下本轮输入时说的话：先拒绝，再告诉用户这里能做什么 */
        private static final String GUARDRAIL_REJECT_TEXT = """
                        抱歉，我只能协助处理运维相关的问题。
                        你可以告诉我哪台设备或哪个系统出了问题、什么时候开始的、看到了什么现象，我来帮你整理成工单。
                        """;

        /** 违规次数超限、会话被主动结束时说的话 */
        private static final String GUARDRAIL_END_TEXT = """
                        抱歉，本次会话已经结束。请点击右上角「重新开始」开启新的会话。
                        """;

        public WorkOrderWorkflowLauncher(ChatModel chatModel, ChatMemory chatMemory, RedisSaver redisSaver,
                        DateTimeTools dateTimeTools,
                        FileClient fileClient)
                        throws GraphStateException {

                chatClient = ChatClient.builder(chatModel)
                                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                                .build();
                this.graph = new StateGraph(GRAPH_NAME, getKeyStrategyFactory())
                                // 护栏永远是最先跑的那一步：无关输入不必交给识图和提取，省 token
                                .addNode("guardrail", new GuardrailNode(chatClient, systemPrompt))
                                // 后续轮次的入口。中断点放在它之前，恢复时本轮的新输入（含图片）
                                // 已经进了状态，护栏之后的边才能据此分流。
                                .addNode("again_guardrail", new GuardrailNode(chatClient, systemPrompt))
                                .addNode("guardrail_reject", new GuardrailReplyNode(GUARDRAIL_REJECT_TEXT))
                                .addNode("guardrail_end", new GuardrailReplyNode(GUARDRAIL_END_TEXT))
                                .addNode("extract", new ExtractNode(chatClient, systemPrompt))
                                .addNode("vision", new VisionNode(chatClient, systemPrompt, fileClient))
                                .addNode("again_vision", new VisionNode(chatClient, systemPrompt, fileClient))
                                .addNode("validate", new ValidateNode())
                                .addNode("ask", new AskNode(chatClient, systemPrompt))
                                .addNode("options", new OptionsNode(chatClient, systemPrompt))
                                .addNode("again_extract", new ExtractNode(chatClient, systemPrompt))
                                .addEdge(START, "guardrail")
                                .addConditionalEdges("guardrail",
                                                AsyncEdgeAction.edge_async(state -> guardrailRoute(state,
                                                                "vision", "extract")),
                                                Map.of("vision", "vision",
                                                                "extract", "extract",
                                                                "guardrail_reject", "guardrail_reject",
                                                                "guardrail_end", "guardrail_end"))
                                .addEdge("vision", "extract")
                                .addEdge("extract", "validate")
                                .addConditionalEdges("validate",
                                                AsyncEdgeAction.edge_async((state) -> state.value("missing", List.class)
                                                                .map(List::isEmpty)
                                                                .map(isEmpty -> isEmpty ? "options" : "ask")
                                                                .orElse("options")),
                                                Map.of("ask", "ask", "options", "options"))
                                .addEdge("ask", "again_guardrail")
                                .addConditionalEdges("options",
                                                AsyncEdgeAction.edge_async((state) -> {
                                                        String userInput = state.value("userInput", String.class)
                                                                        .orElse(null);
                                                        if (userInput != null && !userInput.isBlank()
                                                                        && INT.matcher(userInput).matches()) {
                                                                return "selected";
                                                        }
                                                        return "append";
                                                }),
                                                Map.of("selected", END, "append", "again_guardrail"))
                                .addConditionalEdges("again_guardrail",
                                                AsyncEdgeAction.edge_async(state -> guardrailRoute(state,
                                                                "again_vision", "again_extract")),
                                                Map.of("again_vision", "again_vision",
                                                                "again_extract", "again_extract",
                                                                "guardrail_reject", "guardrail_reject",
                                                                "guardrail_end", "guardrail_end"))
                                .addEdge("again_vision", "again_extract")
                                .addEdge("again_extract", "validate")
                                // 普通拒绝只出文案，随后停在下一轮入口继续等用户补充；
                                // 直接去 END 会让前三次违规就把会话终结掉。
                                .addEdge("guardrail_reject", "again_guardrail")
                                // 真正结束会话：图走到 END，之后的输入会被会话结束标记挡住
                                .addEdge("guardrail_end", END)
                                .compile(CompileConfig.builder()
                                                .saverConfig(SaverConfig.builder().register(redisSaver).build())
                                                // 只留一个恢复点。分流放在护栏之后，本轮图片才会被读到。
                                                .interruptBefore("again_guardrail")
                                                .build());

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
                        // 本轮用户附带的图片地址，VisionNode 从这里取 URL、经 service-files
                        // 读取字节构造多模态 Media。用 ReplaceStrategy 而不是 AppendStrategy：
                        // 它表示"这一轮说了什么"，纯文字轮次也要把上一轮的图片清掉。
                        strategies.put("images", new ReplaceStrategy());
                        // 本轮识图结果（完整字符串）。它只给后端节点用，不进 SSE；
                        // 用 ReplaceStrategy：每轮开始时由输入清空，免得没有图片的轮次
                        // 把上一轮的描述带进 AskNode。
                        strategies.put("vision_res", new ReplaceStrategy());
                        // 护栏的判定结果和累计违规次数；sessionEnded 一旦为 true 就一直保留，
                        // 恢复出来的会话不会再被放行。
                        strategies.put("guardrail", new ReplaceStrategy());
                        strategies.put("violationCount", new ReplaceStrategy());
                        strategies.put("sessionEnded", new ReplaceStrategy());
                        return strategies;
                };
        }

        /**
         * 护栏之后的去向。
         *
         * <p>
         * 分流必须发生在护栏**之后**（恢复之后），而不是 ask/options 的出边上：
         * 那些边在上一轮结束时就已经算好了，那时本轮刚上传的图片还没进状态，
         * 永远会选到"没有图"的那条。
         */
        private static String guardrailRoute(OverAllState state, String visionTarget, String extractTarget) {
                // 没有明确 true 就当被拦下：护栏宁可多挡，也不把无关内容送进识图
                if (!state.value("guardrail", Boolean.class).orElse(false)) {
                        return state.value("sessionEnded", Boolean.class).orElse(false)
                                        ? "guardrail_end"
                                        : "guardrail_reject";
                }
                List<?> images = state.value("images", List.class).orElse(List.of());
                return images.isEmpty() ? extractTarget : visionTarget;
        }

        public Flux<SseElement> stream(String threadId, String userInput, List<String> images) {
                if (threadId == null) {
                        threadId = "wo-" + UUID.randomUUID().toString().replace("-", "");
                }
                RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
                // conversationId 必须和 threadId 一致：它是各节点写 ChatMemory 的 key，
                // 也是客户端拿到的那个 id —— 后续生成工单用同一个 id 才能读到这段对话。
                // 不传的话 ExtractNode 会自己生成一个 UUID，那段记忆就再也没有人能读到了。
                // vision_res 每轮先清空：这一轮没发图时，AskNode 不能把上一轮的识图描述
                // 当成本轮内容说出来。
                return toSse(graph.stream(
                                Map.of("userInput", userInput,
                                                "conversationId", threadId,
                                                "images", images,
                                                "vision_res", ""),
                                config),
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

        /** v1 没有自助解决节点，决策动作只在 v2 里有意义。 */
        @Override
        public Flux<SseElement> decide(String threadId, String action) {
                return Flux.error(new UnsupportedOperationException("v1 工作流不支持自助解决决策"));
        }

        /** v1 的识图结果不做图片筛选，也没有可供建单使用的有效图片列表。 */
        @Override
        public List<String> workOrderImages(String threadId) {
                return List.of();
        }

        private static Flux<SseElement> toSse(Flux<NodeOutput> outputs, String threadId) {
                return outputs
                                .filter(output -> output instanceof StreamingOutput)
                                .cast(StreamingOutput.class)
                                // draft 是节点借一条单元素 Flux 带出来的工单对象，和 ask/options 走同一条通道。
                                // 识图（vision / again_vision）刻意不在其中：它的结果只在后端
                                // 供 AskNode 追问和 ExtractNode 提取使用，不直接展示给用户。
                                .filter(output -> "ask".equals(output.node())
                                                || "options".equals(output.node())
                                                || "draft".equals(output.node())
                                                // 护栏的两种结局分两个节点名：前端据此区分"只是拒绝"
                                                // 和"会话已结束、必须重新开始"。
                                                || "guardrail_reject".equals(output.node())
                                                || "guardrail_end".equals(output.node()))
                                .map(output -> new ChatEvent(output.node(), output.getOriginData()))
                                .subscribeOn(Schedulers.boundedElastic())
                                .cast(SseElement.class)
                                .startWith(Mono.just(new WorkflowInfo(threadId)));
        }
}
