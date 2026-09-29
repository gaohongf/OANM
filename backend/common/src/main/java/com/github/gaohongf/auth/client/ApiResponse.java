package com.github.gaohongf.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 承接本项目的统一响应包装（由 com.lingyun 的 JsonResponseBodyPackerMvcAdapter 生成）。
 * <p>
 * 不直接复用三方的 {@code com.lingyun.base.rsm.message.Response}，因为它的 {@code data}
 * 声明成 {@code Object}，反序列化只会拿到 LinkedHashMap，丢掉类型信息。
 * 这里用泛型保留 data 的真实类型。
 *
 * <h2>为什么必须带上 type 字段</h2>
 * 后端用"HTTP 200 + type=ERROR"表达业务失败（RSM 的统一响应约定），所以只看 HTTP 状态码
 * 是不够的。而 {@code code} 判不了成功：它是消息 id 派生的，同一个接口在不同场景下可能是
 * 1053/1054/1056，没有稳定的白名单。
 * <p>
 * 缺了这个字段时，调用方只能拿到 {@code data}，于是<b>一个业务错误会被当成"查无数据"</b> ——
 * 例如用户服务返回"内部错误"，调用方会以为"这个用户不存在"，还会把这个错误结论<b>缓存住</b>。
 * 这正是 {@code UserLookup} 的契约里明确要区分"查不到"和"没问到"的那件事，
 * 所以 {@link #requireSuccess} 是必须的，不是可选的礼貌检查。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiResponse<T>(String type, Integer code, T data, String msg) {

    /**
     * 是否成功。
     * <p>
     * 判 {@code type} 而不是 {@code code}：type 只有 SUCCESS/WARN/INFO/ERROR 四种取值，
     * 语义明确；code 是消息 id 派生的，没有稳定取值。
     */
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(type);
    }

    /**
     * 不是成功响应就抛异常。
     * <p>
     * 抛（而不是返回 null 或空集合）是为了让调用方能把"失败"和"没有数据"区分开 ——
     * 前者<b>不能写缓存</b>，否则一次抖动会被固化成几十秒的错误结论。
     *
     * @param action 正在做的事，用于异常消息（如"查询用户"）
     */
    public void requireSuccess(String action) {
        if (!isSuccess()) {
            throw new IllegalStateException(action + "失败: type=" + type + " code=" + code + " msg=" + msg);
        }
    }
}
