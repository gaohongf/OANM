package com.github.gaohongf.redis;

/**
 * Redis pub/sub 频道名。
 *
 * <h2>这里只放"信号", 不放数据</h2>
 * 用 pub/sub 传递的是"某某变了, 你自己去重拉"这种通知, 而不是变更后的数据本身。
 * 理由是 pub/sub 是<b>发后即忘</b>的: 订阅者掉线期间的消息会永久丢失。所以每条通知都必须
 * 能由接收方自己去权威来源重新拉取 —— 通知丢了顶多晚一点, 不会导致数据不一致。
 */
public final class RedisChannels {

    /**
     * 授权数据变了。
     * <p>
     * 载荷: {@code *} 表示"影响面不确定, 全量失效"; 否则是受影响的 userId（数字）。
     * <p>
     * 接收方是各个业务服务（含 service-auth 的其他实例）—— 它们各自持有
     * {@code RemoteStpInterface} / {@code StpInterfaceImpl} 的本地授权缓存。
     */
    public static final String AUTHORITIES_CHANGED = "oanm:authorities:changed";

    /**
     * 网关路由变了。
     * <p>
     * 载荷: 版本号。网关收到后回 service-auth 重拉全量路由。
     * <p>
     * 接收方是 api-gateway。它<b>不能</b>依赖 common（common 是 MVC 技术栈, 网关是 WebFlux,
     * 混在一起会启动失败）, 所以频道名在网关侧有一份手写的副本 —— 改动时两边都要改。
     */
    public static final String ROUTES_CHANGED = "oanm:routes:changed";

    private RedisChannels() {
    }
}
