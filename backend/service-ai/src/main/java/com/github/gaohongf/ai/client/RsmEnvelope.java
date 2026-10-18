package com.github.gaohongf.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 工单服务统一的响应信封 {@code {type, code, data, msg}}。
 *
 * <p>
 * 只声明本服务关心的字段；{@code ignoreUnknown = true} 让对端以后加字段时不会把这里打挂。
 * 泛型 {@code T} 由调用处的 {@code ParameterizedTypeReference} 指定。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RsmEnvelope<T>(String type, Integer code, T data, String msg) {

    private static final String SUCCESS = "SUCCESS";

    public boolean success() {
        return SUCCESS.equals(type);
    }
}
