package com.github.gaohongf.auth.satoken;

/**
 * 进程内的授权缓存, 可以被外部信号失效。
 *
 * <h2>为什么要有这个抽象</h2>
 * 两个实现分别在不同服务里生效:
 * <ul>
 *   <li>{@code RemoteStpInterface}（common）—— 普通服务用它, 授权数据来自 service-auth</li>
 *   <li>{@code StpInterfaceImpl}（service-auth）—— service-auth 自己用它, 直接查本地库</li>
 * </ul>
 * Redis 广播的监听器需要"失效本进程的授权缓存", 但它不应该认识这两个具体的类:
 * 它只能用 {@code RemoteStpInterface}, 而 service-auth 里根本没有那个 bean。
 * <p>
 * 特别是 service-auth <b>多实例</b>的场景: 实例 A 上管理员改了权限, 实例 B 的
 * {@code StpInterfaceImpl} 缓存也必须失效 —— 它靠本地的 Spring 事件是收不到的,
 * 只能靠 Redis 广播。所以监听器必须能作用于两者。
 */
public interface LocalAuthorityCache {

    /** 失效某个用户的缓存 */
    void invalidateUser(Long userId);

    /** 全量失效（影响面不确定的变更, 或收到了 {@code *} 广播） */
    void invalidateAll();
}
