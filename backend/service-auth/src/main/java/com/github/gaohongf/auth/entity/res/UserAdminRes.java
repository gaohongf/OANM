package com.github.gaohongf.auth.entity.res;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户（管理端视图）。
 *
 * <h2>为什么带上 roles 而不是让界面再查一次</h2>
 * 用户列表要显示"这个人有哪些角色"，而分配角色的弹窗又要以它作为初始选中值。
 * 分两次查意味着打开弹窗时还要再发一个请求，而且两个请求之间数据可能已经变了
 * （列表显示的和弹窗里的不一致）。
 * <p>
 * 后端一次读出整页用户的角色，靠的是一次 `IN (...)` 查询后按 user_id 分组，
 * 不是每个用户查一次 —— 否则一页 20 个用户就是 20 次查询。
 *
 * <h2>id 为什么是 String 而不是 Long</h2>
 * 理由与 {@code WorkOrderRes} 上那段相同: 雪花 id 序列化成 JSON 数字后 JS 端会丢精度。
 * 这里要紧是因为前端会把它拼进 {@code PUT /users/{id}/roles} —— 分配角色的对象可能因此错人。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAdminRes {

    private String id;

    private String username;

    private String nickname;

    /** 是否被封号 */
    private Boolean locked;

    /** 该用户当前拥有的角色名 */
    private List<String> roles;
}
