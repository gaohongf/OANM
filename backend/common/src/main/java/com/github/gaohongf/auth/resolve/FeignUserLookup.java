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
        if (response == null) {
            throw new IllegalStateException("查询用户 " + id + " 失败: 响应为空");
        }
        // 必须检查信封: 后端的业务失败是 "HTTP 200 + type=ERROR"，只看状态码看不出来。
        // 不检查的话，"内部错误"会被当成"查无此人"，还会被负缓存住 —— 与 UserLookup
        // 契约里"null 表示确实不存在、失败必须抛"的分工正好相反。
        response.requireSuccess("查询用户 " + id);
        return response.data();
    }
}
