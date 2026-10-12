package com.github.gaohongf.auth.res;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户(对外公开的那几个字段)。既是 {@code /auth/internal/users/{id}} 的响应,
 * 也是 {@code @User} 解析后嵌进其它响应里的形状。
 *
 * <h2>id 为什么是 String 而不是 Long</h2>
 * 理由与 {@code WorkOrderRes} 上那段相同: 用户 id 是雪花 id, 序列化成 JSON 数字后
 * JS 端会丢精度。这个字段出现在 {@code @User} 的降级形态里({@code {"id":...}},
 * 见 common 的解析器), 前端拿它显示"id=xxx"作为兜底 —— 显示错的值比显示不出来更坏。
 * <p>
 * 注意解析器内部仍然用 {@code Long} 做键({@code UserResolveStrategy} 的
 * {@code Map<Long, UserRes>}, 键来自库里的 {@code create_by} 列), 只有序列化这一步是字符串。
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserRes {
    private String id;
    private String username;
    private String nickname;
    private Boolean locked;
}
