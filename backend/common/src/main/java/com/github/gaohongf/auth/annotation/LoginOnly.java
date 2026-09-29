package com.github.gaohongf.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个端点<b>只要登录就能访问</b>, 不需要任何具体权限。
 *
 * <h2>为什么需要它（而不是靠授权解决）</h2>
 * 没有这个标记时只有两档: {@link IsOpen}（完全公开）和"必须有某条权限"。
 * 但有一类端点天然属于第三档, 最典型的是 {@code /api/auth/me} ——
 * 前端登录后要靠它拿自己的菜单和权限, 所以它必须对<b>任何已登录用户</b>可用, 包括
 * 一个角色都没有的新用户。
 * <p>
 * 想靠授权表达这件事是行不通的: 那意味着每个角色（以及将来每个新建的角色）都必须被授予
 * {@code GET:/api/auth/me}, 漏一个就会有用户的界面加载不出来, 而且症状是"登录成功但进去白屏",
 * 很难联想到权限配置。
 *
 * <h2>与 {@link IsOpen} 的区别</h2>
 * <ul>
 *   <li>{@link IsOpen}: <b>不检查登录态</b>, 任何人都能访问。用于登录接口本身、内部端点。</li>
 *   <li>{@code @LoginOnly}: <b>要求已登录</b>, 但不要求具体权限。用于 /me、登出这类"用户操作自己"的接口。</li>
 * </ul>
 * 别把两者混用: 给 {@code /me} 标 {@link IsOpen} 会让未登录请求也进入方法体,
 * 然后在取登录态时抛异常, 报出来的错和"没登录"完全对不上。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginOnly {
}
