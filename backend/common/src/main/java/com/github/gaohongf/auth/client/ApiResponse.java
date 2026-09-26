package com.github.gaohongf.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 承接本项目的统一响应包装（由 com.lingyun 的 JsonResponseBodyPackerMvcAdapter 生成）。
 * <p>
 * 不直接复用三方的 {@code com.lingyun.base.rsm.message.Response}, 因为它的 {@code data}
 * 声明成 {@code Object}, 反序列化只会拿到 LinkedHashMap, 丢掉类型信息。
 * 这里用泛型保留 data 的真实类型, 同时 ignoreUnknown 以容忍对方的 {@code type} 等字段。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiResponse<T>(Integer code, T data, String msg) {
}
