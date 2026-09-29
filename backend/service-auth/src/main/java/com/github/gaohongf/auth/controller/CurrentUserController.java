package com.github.gaohongf.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.auth.annotation.LoginOnly;
import com.github.gaohongf.auth.entity.res.CurrentUserRes;
import com.github.gaohongf.auth.res.UserAuthorities;
import com.github.gaohongf.auth.rsm.AuthRsm;
import com.github.gaohongf.auth.service.MenuService;
import com.github.gaohongf.auth.service.UserService;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;

/**
 * 当前登录用户自己的接口。
 * <p>
 * 这两个端点都标 {@link LoginOnly} 而不是 {@code @IsOpen}, 也不是"需要某条权限":
 * 它们必须对<b>任何已登录用户</b>可用, 包括一个角色都没有的新用户 ——
 * 否则登录成功之后前端拿不到菜单, 界面直接白屏, 而症状"登录成功却打不开首页"
 * 很难让人联想到是权限配置问题。见 {@link LoginOnly} 的说明。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class CurrentUserController {

    private final UserService userService;
    private final MenuService menuService;

    /**
     * 当前用户上下文: 用户信息 + 角色 + 权限键 + 可见菜单树。
     * <p>
     * 前端登录后调一次, 用返回的菜单动态注册路由、用权限键控制按钮显隐。
     */
    @LoginOnly
    @GetMapping("/me")
    public CurrentUserRes me() {
        Long userId = StpUtil.getLoginIdAsLong();
        UserAuthorities authorities = userService.findAuthorities(userId);
        return new CurrentUserRes(
                userService.findUser(userId),
                authorities.roles(),
                authorities.permissions(),
                menuService.treeForCurrentUser());
    }

    /**
     * 登出。同样只需要登录态 —— 未登录的人调这个本来也无事可做。
     */
    @LoginOnly
    @ExecutionSuccess(AuthRsm.LOGOUT_SUCCESS)
    @ExecutionFailed(AuthRsm.LOGOUT_FAIL)
    @PostMapping("/logout")
    public void logout() {
        StpUtil.logout();
    }
}
