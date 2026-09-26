package com.github.gaohongf.auth.resolve;

import com.github.gaohongf.auth.res.UserRes;

/**
 * "按 id 取用户"这件事的取数口, 与传输方式无关。
 *
 * <h2>为什么要有这层</h2>
 * {@link UserResolveStrategy} 的职责是缓存和降级, 不该关心用户是从 HTTP 拿的还是从本进程数据库拿的。
 * 拆开之后:
 * <ul>
 *   <li>普通服务用 {@link FeignUserLookup} —— 走 HTTP 打回 service-auth。</li>
 *   <li>service-auth 自己用本地实现直接查库, 不必为了取自己的数据绕一圈网络,
 *       也就不需要在 service-auth 里塞一个负载均衡器只为让自己能调自己。</li>
 * </ul>
 *
 * <h2>必须区分"查不到"和"没问到"</h2>
 * 返回值 {@code null} 表示<b>这个人确实不存在</b>, 调用方可以放心地把它当结果缓存住
 * （负缓存, 否则一个不存在的 id 会每次都穿透到库）。
 * 而"没问到"（超时、连接被拒、服务未注册）必须<b>抛异常</b>—— 如果也返回 null,
 * 一次抖动就会把"查不到"缓存下来, 表现得像用户真的消失了, 直到 TTL 过期才恢复。
 * 实现时不要为了"稳"而把异常吞成 null。
 */
public interface UserLookup {

    /**
     * @return 对应用户; {@code null} 表示该 id 确实没有对应用户
     * @throws RuntimeException 传输/依赖不可用时抛出, 由调用方决定降级方式
     */
    UserRes findById(Long id);
}
