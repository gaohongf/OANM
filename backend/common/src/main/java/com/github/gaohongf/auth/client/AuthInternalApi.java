package com.github.gaohongf.auth.client;

/**
 * service-auth 内部端点路径。
 * <p>
 * 客户端（{@link UserClient}）与服务端 Controller 共用这里的常量, 避免两边路径写歪 ——
 * 写歪的症状是 Feign 侧的 404, 而服务本身完全正常, 排查起来很费时间。
 * <p>
 * 这些端点走 {@code @IsOpen} 不做鉴权, <b>不允许</b>从网关暴露出去。
 * <p>
 * 靠什么保证这一点: 路径<b>不以 {@code /api} 开头</b>, 而网关的路由谓词只匹配 {@code /api/**}。
 * 所以这些端点在网关侧根本匹配不到路由 —— 不依赖"记得关掉 {@code discovery.locator}"这类开关
 * 的正确性。新增内部端点时请沿用 {@code /auth/internal/**} 这个前缀, 见 standard.md 的路径约定。
 */
public final class AuthInternalApi {

    /** 按 id 查单个用户, 响应 data 为 UserRes */
    public static final String USER_BY_ID = "/auth/internal/users/{id}";

    /** 按 id 查用户的角色与权限, 响应 data 为 UserAuthorities */
    public static final String USER_AUTHORITIES = "/auth/internal/users/{id}/authorities";

    /** 全量启用中的网关路由, 响应 data 为 GatewayRouteRes 列表（网关用） */
    public static final String ROUTES = "/auth/internal/routes";

    /**
     * 路由版本号, 响应 data 为字符串（网关轮询兜底比对用）。
     * <p>
     * 网关靠 Redis 广播即时感知变更, 但 pub/sub 是发后即忘的 —— 订阅者掉线期间的消息会丢。
     * 所以它还要定期拿这个版本号比对一次, 不一致就重拉, 作为兜底。
     */
    public static final String ROUTES_VERSION = "/auth/internal/routes/version";

    /**
     * 启用中的路由路径模式列表, 响应 data 为字符串列表（如 {@code ["/api/ops/**"]}）。
     * <p>
     * 服务启动自检用它判断"我的端点有没有网关路由能到达" —— 一个端点若不被任何路由覆盖,
     * 从外部访问就是 404, 而服务自己完全正常, 排查方向很容易跑偏。
     */
    public static final String ROUTE_PATTERNS = "/auth/internal/routes/patterns";

    /**
     * 服务上报自己需要权限的端点（自注册）。
     * <p>
     * 正常路径是 Kafka（见 {@code RegisterServiceAuth}），这个 HTTP 端点用于两种场景:
     * 消息已过 retention 需要补报、以及 Kafka 不可用时的即时兜底。
     */
    public static final String REGISTER = "/auth/internal/register";

    /** 上面路径的控制器前缀 */
    public static final String USERS = "/auth/internal/users";

    /**
     * 按角色名查用户 id 列表, 响应 data 为 id 列表。
     * <p>
     * <b>是相对于 {@link #USERS} 的中段, 不是完整路径</b> —— {@code UserClient} 的
     * {@code @FeignClient(path = USERS)} 已经把前缀吃掉了, 那边只能写相对路径;
     * 服务端的 {@code InternalUserController} 也是同样的 {@code @RequestMapping(USERS)}。
     * 所以这个常量两边都能直接用, 这是它不带前缀的原因（写成完整路径会让 Feign 侧拼成
     * {@code /auth/internal/users/auth/internal/users/...}）。
     * <p>
     * 用途是"通知某个角色的全部成员"：由生产方调它把角色展开成收件人列表。
     * 收件人解析刻意放在生产方 —— 消息模块因此不需要在消费路径上依赖 service-auth 的可用性。
     */
    public static final String USERS_BY_ROLE = "/by-role/{roleName}";

    private AuthInternalApi() {
    }
}
