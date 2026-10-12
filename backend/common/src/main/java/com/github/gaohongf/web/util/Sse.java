package com.github.gaohongf.web.util;

import java.util.Map;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.lang.NonNull;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class Sse {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static final String EVENT_START = "start";
    public static final String EVENT_DELTA = "delta";
    public static final String EVENT_DONE = "done";
    public static final String EVENT_ERROR = "error";
    /**
     * 站内信推送。事件名与前端 {@code sse.ts} 的 {@code switch} 逐字对应 ——
     * 那边对不认识的事件名是<b>安静跳过</b>的, 所以这里改了名字不会报错, 只会静默收不到。
     */
    public static final String EVENT_NOTIFICATION = "notification";

    public static final String DONE_REASON_KEY = "reason";
    public static final String DONE_REASON_SUCCESS = "success";
    public static final String DONE_REASON_CANCEL = "cancel";

    public static ServerSentEvent<String> start(SsePayload payload) {
        return standardize(EVENT_START, toJson(payload));
    }

    public static ServerSentEvent<String> done(String reason) {
        return standardize(EVENT_DONE, toJson(Map.of(DONE_REASON_KEY, reason)));
    }

    public static ServerSentEvent<String> delta(String text) {
        return standardize(EVENT_DELTA, text);
    }

    public static ServerSentEvent<String> delta(Map<String, Object> map) {
        return standardize(EVENT_DELTA, toJson(map));
    }

    public static ServerSentEvent<String> error(String error) {
        return standardize(EVENT_ERROR, error);
    }

    /**
     * 一条站内信。
     * <p>
     * 收的是<b>已经序列化好的 JSON</b> 而不是对象: 站内信里有时间字段, 而本类那个静态
     * {@link ObjectMapper} 没装 JavaTimeModule, 拿它序列化 {@code LocalDateTime} 会抛异常
     * 然后被 {@link #toJson} 吞成 {@code "{}"} —— 客户端会收到一条内容为空的消息, 且不报错。
     * 所以序列化交给调用方用 Spring 容器里那个 ObjectMapper（它装了 JavaTimeModule,
     * 也带着本项目的序列化定制）。
     *
     * @param json 一条消息的 JSON
     */
    public static ServerSentEvent<String> notification(String json) {
        return standardize(EVENT_NOTIFICATION, json);
    }

    /**
     * sse唯一的构建方法
     * 注意！！！
     * <p>
     * SSE 规范规定：客户端解析时，要丢掉字段冒号后面的<b>第一个空格</b>（如果有的话）。
     * 也就是说 {@code data: hello} 解析出来是 {@code "hello"}，那个空格只是分隔符。
     *
     * <p>
     * 而 Spring 写报文时代码是（{@code ServerSentEventHttpMessageWriter}）：
     * 
     * <pre>
     *   text = StringUtils.replace(text, "\n", "\ndata:");   // ← 注意是 "data:" 不是 "data: "
     *   encodeText(sseText + text + "\n\n", ...);
     * </pre>
     * 
     * 拼出来就是 {@code data:内容}，<b>冒号后面没有分隔空格</b>。
     * 于是内容自己的前导空格就会被客户端当作"分隔符"吃掉：
     * 
     * <pre>
     *   我们想发的 content   Spring 写出的报文   客户端解析回来的
     *   ───────────────────  ─────────────────   ───────────────
     *   "The"                data:The            "The"     ✅
     *   " test"              data: test          "test"    ❌ 前导空格没了
     *   " "                  data:                ""        ❌ 整个空格没了
     * </pre>
     * 
     * 而模型的 token 里"空格"经常就是独立的一个 chunk，或者附着在下一个词前面 ——
     * 结果就是英文单词全粘在一起：{@code TheAPItesthasbeen}。
     *
     * <p>
     * 所以这里主动在 data 前面补一个空格，让实际写出的报文变成
     * {@code data:  test}（两个空格：第一个是分隔符，第二个才是数据本身），
     * 客户端按规范丢掉一个之后，拿到的正好是原来的 {@code " test"}。
     *
     * <p>
     * 这个办法对三种情况都成立：
     * 
     * <pre>
     *   数据       报文（含本方法补的分隔空格）   解析结果
     *   ────────  ──────────────────────────   ────────
     *   "abc"     data: abc                     "abc"     ✅
     *   " abc"    data:  abc                    " abc"    ✅
     *   " "       data:   (共 3 个空格)          " "       ✅
     *   多行       data: a\ndata:\ndata:b       "a\n\nb"  ✅ 中间的空行也保住了
     * </pre>
     * 
     * 
     * @param event 事件名称
     * @param data  数据
     * @return
     */
    private static ServerSentEvent<String> standardize(@NonNull String event, String data) {
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(" " + (data == null ? "" : data))
                .build();
    }

    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
