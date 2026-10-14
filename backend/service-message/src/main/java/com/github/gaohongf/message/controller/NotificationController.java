package com.github.gaohongf.message.controller;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.github.gaohongf.auth.annotation.LoginOnly;
import com.github.gaohongf.auth.model.CurrentLoginId;
import com.github.gaohongf.message.entity.req.NotificationReadState;
import com.github.gaohongf.message.service.NotificationService;
import com.github.gaohongf.message.sse.NotificationSseRegistry;
import com.github.gaohongf.web.rsm.ReactiveRsmWriter;
import com.github.gaohongf.web.util.Sse;
import com.github.gaohongf.web.util.SsePayload;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.message.Response;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 站内信收件箱。
 *
 * <h2>为什么每个端点都标 {@code @LoginOnly} 而不是各自一条权限</h2>
 * 读自己的收件箱不需要"被授予"任何东西 —— 它和"取自己的菜单"是同一类端点。
 * 所以这里不需要权限行, 也就没有"新建用户必须先授一个站内信权限才能收到通知"这种荒唐的前置条件。
 * 副作用是好的: 端点自注册的扫描器会跳过 {@code @LoginOnly}（它只收需要权限的端点）,
 * 于是权限管理界面里不会出现一堆没人该去授权的垃圾行。
 *
 * <h2>身份只有一个来源: 登录态</h2>
 * {@code receiver} 一律来自 {@code CurrentLoginId.require(exchange)}, 永不接受入参 ——
 * 全部查询和更新的 where 里都带着它, 所以换个 id 也读不到、改不了别人的消息。
 * <p>
 * 这里出现 {@code ServerWebExchange} 是刻意的: 反应式栈上 {@code StpUtil} 在控制器里
 * 一定取不到值（理由见 {@code CurrentLoginId} 的类注释, 结论有字节码为证）,
 * 登录 id 只能由鉴权适配器写进请求属性。
 *
 * <h2>为什么每个端点都要 {@code ReactiveRsmWriter} 包一下</h2>
 * WebFlux 没有 servlet 那个 {@code ResponseBodyAdvice} 扩展点, 所以普通对象返回时
 * <b>不会</b>被自动包成 RSM 信封, 而前端的 {@code request<T>()} 是按信封拆的
 * （它连判成功都看 {@code type === 'SUCCESS'}）。理由与证据在
 * {@link ReactiveRsmWriter} 的类注释里。这里的 {@code /stream} 是唯一不包的端点 ——
 * 它的响应体是 SSE 帧, 不是 JSON。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/message/notifications")
public class NotificationController {

    /**
     * 心跳间隔。
     * <p>
     * 与 AI 的两个流式端点同样的做法, 但这里的必要性更强: AI 的流几乎一直在吐数据,
     * 而站内信的流可能安静一整天。中间任何一层（nginx、网关、负载均衡）都会把
     * "长时间没有字节的 TCP 连接"当成死连接掐掉, 而掐掉的那一刻双方都不会报错 ——
     * 用户那边的表现是"铃铛再也不响了, 刷新一下又好了"。
     */
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final NotificationService notificationService;
    private final NotificationSseRegistry sseRegistry;
    private final ReactiveRsmWriter rsmWriter;

    /** 收件箱分页。 */
    @LoginOnly
    @GetMapping
    public Response page(ServerWebExchange exchange,
            @RequestParam(name = "current", defaultValue = "1") long current,
            @RequestParam(name = "size", defaultValue = "10") long size,
            @RequestParam(name = "readState", required = false) String readState) {
        Long receiver = CurrentLoginId.require(exchange);
        return rsmWriter.success(exchange, GenericRsm.QUERY_SUCCESS,
                notificationService.page(receiver, current, size, NotificationReadState.parse(readState)));
    }

    /** 未读数。铃铛角标靠它, 也是 SSE 断线期间唯一的兜底（前端 30 秒轮询一次）。 */
    @LoginOnly
    @GetMapping("/unread-count")
    public Response unreadCount(ServerWebExchange exchange) {
        Long receiver = CurrentLoginId.require(exchange);
        return rsmWriter.success(exchange, GenericRsm.QUERY_SUCCESS,
                notificationService.unreadCount(receiver));
    }

    /** 单条已读。幂等, 重复调用不会报错（见 {@code NotificationService#markRead}）。 */
    @LoginOnly
    @PostMapping("/{id}/read")
    public Response markRead(ServerWebExchange exchange, @PathVariable("id") Long id) {
        Long receiver = CurrentLoginId.require(exchange);
        notificationService.markRead(receiver, id);
        // data 传 null: 这个操作的"结果"就是"没有结果", 返回一个数字（受影响行数）反而会让
        // 前端开始判断"是不是没生效"—— 那属于界面该自己刷新的职责。
        return rsmWriter.success(exchange, GenericRsm.UPDATE_SUCCESS, null);
    }

    /** 全部已读。返回受影响行数。 */
    @LoginOnly
    @PostMapping("/read-all")
    public Response markAllRead(ServerWebExchange exchange) {
        Long receiver = CurrentLoginId.require(exchange);
        return rsmWriter.success(exchange, GenericRsm.UPDATE_SUCCESS,
                notificationService.markAllRead(receiver));
    }

    /**
     * 消息推送流。
     *
     * 首帧 {@code start} + 业务事件 + 定期心跳, 全部由 {@link Sse} 构造
     * （那个类里有一条关于"data 前导空格"的坑, 复用它是为了让规则只有一处）。
     *
     * <h2>首帧里的 id/model 是空的</h2>
     * {@code SsePayload} 是给 AI 会话用的（{@code id} 是 conversationId, {@code model} 是模型名）,
     * 站内信流没有这两个东西。仍然发这一帧是因为前端的解析器把它当作"流已建立"的信号
     * （用来复位重连退避）—— 换成别的形状就得在客户端多写一条分支, 而那条分支唯一的用途
     * 就是判断"连上了没有"。
     *
     * <h2>这个端点不加信封</h2>
     * 响应体是 SSE 帧。包成 JSON 信封的话流就再也流不出来了, 而症状是"请求一直挂着"
     * （前端等一个永远不会结束的 JSON 正文）。这也是 {@link ReactiveRsmWriter} 不做成
     * 全局包装器的那条理由, 见那个类。
     */
    @LoginOnly
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(ServerWebExchange exchange) {
        Long receiver = CurrentLoginId.require(exchange);

        Mono<ServerSentEvent<String>> opening = Mono.justOrEmpty(
                Sse.start(new SsePayload(null, null, System.currentTimeMillis())));
        Flux<ServerSentEvent<String>> notifications = sseRegistry.register(receiver);
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                // 注释帧（`:` 开头的行）: SSE 规范里客户端必须忽略它, 前端的解析器也确实
                // 遇到 `:` 就跳过。所以它只承担"让这条 TCP 连接上有字节在动"的作用,
                // 不会混进业务数据里。
                .map(tick -> ServerSentEvent.<String>builder().comment("keep-alive").build());

        // 首帧必须排在前面, 所以要 concat 而不是 merge; 而后两个都是无限流, 只能 merge ——
        // 用 concat 连接它们的话第二个永远轮不到（第一个永不完成）。
        return Flux.concat(opening, Flux.merge(notifications, heartbeat));
    }
}
