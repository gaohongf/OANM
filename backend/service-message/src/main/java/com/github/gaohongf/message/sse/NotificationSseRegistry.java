package com.github.gaohongf.message.sse;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 当前进程内的 SSE 连接登记表: 谁在线, 以及往哪条流里写。
 *
 * <h2>多实例边界（这是本轮刻意接受的限制）</h2>
 * 表在<b>进程内</b>, 消息却在 Kafka 上被<b>某一个</b>实例消费。所以:
 * <pre>
 *   实例 A 消费到"给用户 7 的消息" → 写进 A 的表 → 用户 7 连在 B 上 → B 的表里没人 → 收不到
 * </pre>
 * 这不是数据丢失: 消息已经落库, 用户的未读数走的是数据库, <b>最迟 30 秒</b>（前端的轮询兜底）
 * 一定会对齐。SSE 在这里只承担"更快知道", 权威始终是库。
 * <p>
 * 要补这一段, 需要把推送信号经过 Redis pub/sub 广播给所有实例（每个实例再查自己的表）。
 * 那样做的时候<b>本类的 API 不用改</b> —— 中继是"往 push 的入口挂一个广播", 不是换掉登记表。
 * 所以现在不做: 多实例部署之前, 它带来的复杂度（Redis 连接、跨实例消息格式、实例标识）
 * 换不到任何可见的收益。
 *
 * <h2>为什么用 Sinks 而不是 {@code Flux.create} + 回调列表</h2>
 * 推送是从 <b>Kafka 消费线程</b>发出的, 而 {@code tryEmitNext} 明确是线程安全且不阻塞的
 * （失败只体现在返回值里, 不抛异常）。手写回调列表就得自己处理"连上/断开与推送并发",
 * 而那正是最容易写出"偶尔少推一条"的地方。
 */
@Slf4j
@Component
public class NotificationSseRegistry {

    /**
     * 单个用户最多保留几条连接。
     * <p>
     * 同一个用户开多个标签页是常态, 所以不能只留一条; 但它是有上限的 —— 否则一个
     * 反复重连的客户端（比如前端重连逻辑写坏了）会在服务端攒下无数条永远没人读的流。
     * <p>
     * 超出时的策略是<b>挤掉最早的那条</b>而不是"拒绝新连接": 拒绝的后果是新连接立刻断开,
     * 而前端的重连逻辑会立刻再试 —— 于是变成一个打不进去的死循环, 用户看到的界面
     * 永远是"连不上", 而服务端日志上什么都看不出来。挤掉旧连接至少让最新的那个能用。
     */
    private static final int MAX_CONNECTIONS_PER_USER = 5;

    /**
     * 每条连接最多缓冲几条没发出去的消息。
     * <p>
     * 缓冲是为了覆盖"登记之后、客户端真正开始读之前"那个窗口（控制器里首帧要先发出去）。
     * 上限存在的意义是慢客户端: 缓冲满了 Reactor 会把这条流<b>终止</b>, 客户端因此读到流结束,
     * 于是按前端的重连逻辑退避重连 —— 这比"无限缓冲直到 OOM"和"静默丢弃"都好。
     */
    private static final int BUFFER_SIZE = 64;

    /** userId → 该用户的连接（按登记顺序, 队头最旧） */
    private final Map<Long, Queue<Sinks.Many<ServerSentEvent<String>>>> connections = new ConcurrentHashMap<>();

    /**
     * 登记一条新连接, 返回要写回响应的流。
     * <p>
     * 返回的流被取消（浏览器关页面、网络断）时自动摘除登记 —— 登记表的生命周期
     * 因此完全绑在连接上, 不需要任何清理任务。
     */
    public Flux<ServerSentEvent<String>> register(Long userId) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE);

        Queue<Sinks.Many<ServerSentEvent<String>>> queues = connections.computeIfAbsent(userId,
                id -> new ConcurrentLinkedQueue<>());
        queues.add(sink);
        evictExcess(userId, queues);

        return sink.asFlux().doFinally(signal -> unregister(userId, sink));
    }

    /**
     * 往某个用户的所有连接推一条消息。没有在线连接时什么也不做（这是常态, 不是异常）。
     * <p>
     * 线程安全且不阻塞, 所以从 Kafka 消费线程直接调用是安全的。
     */
    public void push(Long userId, ServerSentEvent<String> event) {
        Queue<Sinks.Many<ServerSentEvent<String>>> queues = connections.get(userId);
        if (queues == null) {
            return;
        }
        for (Sinks.Many<ServerSentEvent<String>> sink : queues) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                // TERMINATED 是断开过程中的正常现象（取消信号还没走到 doFinally 的摘除）,
                // 记 debug 就够了; 其余（缓冲溢出）意味着客户端读得太慢, 那条流会被终止,
                // 客户端重连后靠轮询对齐, 值得留一条 warn。
                if (result == Sinks.EmitResult.FAIL_TERMINATED) {
                    log.debug("推送时连接已终止, 跳过。userId={}", userId);
                } else {
                    log.warn("推送失败, 该连接即将断开并重连。userId={} result={}", userId, result);
                }
            }
        }
    }

    /** 当前登记的连接数, 供测试与排查（比如"这个用户到底连上了没有"） */
    public int connectionCount(Long userId) {
        Queue<Sinks.Many<ServerSentEvent<String>>> queues = connections.get(userId);
        return queues == null ? 0 : queues.size();
    }

    /**
     * 挤掉最旧的连接, 直到不超过上限。
     * <p>
     * 先入队再挤, 所以被挤掉的永远是"别人", 刚登记的这条一定留得下。
     */
    private void evictExcess(Long userId, Queue<Sinks.Many<ServerSentEvent<String>>> queues) {
        while (queues.size() > MAX_CONNECTIONS_PER_USER) {
            Sinks.Many<ServerSentEvent<String>> oldest = queues.poll();
            if (oldest == null) {
                // 并发下被 doFinally 摘空了, 没有可挤的对象
                return;
            }
            oldest.tryEmitComplete();
            log.warn("用户 {} 的 SSE 连接数超过 {}, 已断开最早的一条让其重连", userId, MAX_CONNECTIONS_PER_USER);
        }
    }

    /**
     * 摘除一条连接。
     * <p>
     * 整个"移除 + 空了就删条目"放在 {@code computeIfPresent} 里: 这段读改写不能和
     * 并发的登记/摘除交错, 否则会出现"队列里还有连接, 条目却被删掉了"——
     * 症状是那个用户从此收不到推送, 而登记表看起来一切正常。
     */
    private void unregister(Long userId, Sinks.Many<ServerSentEvent<String>> sink) {
        connections.computeIfPresent(userId, (id, queues) -> {
            queues.remove(sink);
            return queues.isEmpty() ? null : queues;
        });
    }
}
