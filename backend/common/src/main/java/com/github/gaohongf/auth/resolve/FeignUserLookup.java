package com.github.gaohongf.auth.resolve;

import com.github.gaohongf.auth.client.ApiResponse;
import com.github.gaohongf.auth.client.UserClient;
import com.github.gaohongf.auth.res.UserRes;

/**
 * 走 Feign 回源 service-auth 的取数口。
 * <p>
 * 响应体会被 lingyun 的 {@code JsonResponseBodyPackerMvcAdapter} 包成
 * {@code {code,data,msg,type}}, 所以这里解一层 {@link ApiResponse}。
 * 刻意不去依赖三方的 {@code com.lingyun.base.rsm.message.Response} ——
 * 它的 {@code data} 是 {@code Object}, Jackson 只会给你一个 LinkedHashMap。
 * <p>
 * 注意 {@code data} 为 null 是合法结果（查无此人）, 不能当成错误抛出去。
 */
public class FeignUserLookup implements UserLookup {

    private final UserClient userClient;

    public FeignUserLookup(UserClient userClient) {
        this.userClient = userClient;
    }

    @Override
    public UserRes findById(Long id) {
        ApiResponse<UserRes> response = userClient.findById(id);
        return response == null ? null : response.data();
    }
}
