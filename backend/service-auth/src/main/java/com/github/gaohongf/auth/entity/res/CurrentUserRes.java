package com.github.gaohongf.auth.entity.res;

import java.util.List;

import com.github.gaohongf.auth.res.UserRes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户的全部上下文，前端登录后一次拿全。
 *
 * <h2>为什么合成一个接口</h2>
 * 前端初始化需要四样东西: 用户信息、角色、权限键、菜单树。分成四个接口意味着四个往返、
 * 四次可能的失败, 而且它们是<b>互相依赖</b>的 —— 菜单里该显示什么取决于权限键。
 * 分开拿会出现"菜单已渲染但权限还没到"的中间态, 界面会闪一下。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CurrentUserRes {

    /** 用户基本信息（不含密码等敏感字段, 见 {@code UserRes}） */
    private UserRes user;

    /** 角色名列表 */
    private List<String> roles;

    /**
     * 权限键列表, 如 {@code ["GET:/api/ops/work_order/{id}"]}。
     * <p>
     * 前端的按钮级权限判断直接用它, 不需要再请求一次。
     * <p>
     * 注意持有超级权限时这里会是 {@code ["*"]} —— 前端做按钮判断时不能只做精确匹配,
     * 否则超管会看不到任何按钮。判断方式应当与后端一致: 拿键去和列表里的模式逐个通配比对。
     */
    private List<String> permissions;

    /**
     * 当前用户可见的菜单树。
     * <p>
     * 已经按权限过滤过: 无权限的节点不会出现, 且父节点被过滤时其子节点也一并消失
     * （子菜单挂在看不见的父菜单下没有意义）。
     */
    private List<MenuRes> menus;
}
