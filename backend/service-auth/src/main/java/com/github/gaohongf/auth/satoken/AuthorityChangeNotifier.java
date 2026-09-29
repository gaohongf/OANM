package com.github.gaohongf.auth.satoken;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

/**
 * 授权变更的统一通知出口：所有改动授权数据的 Service 都只调这里。
 *
 * <h2>为什么要有这一层</h2>
 * 授权数据被改掉之后, 有两处缓存必须失效, 否则管理员会看到"改了没生效":
 * <ol>
 *   <li>本服务的 {@link StpInterfaceImpl} 本地缓存（30 秒 TTL）</li>
 *   <li>其他服务的 {@code RemoteStpInterface} 本地缓存（各自 30 秒 TTL）</li>
 * </ol>
 * 让每个改授权的地方自己去记这两件事, 迟早会漏。所以收敛成一个出口。
 *
 * <h2>这里只负责"说一声", 不负责"做什么"</h2>
 * 真正的失效动作在 {@link AuthorityCacheInvalidator} 里。这样分层不是洁癖, 是为了
 * 避免循环依赖: 失效的目标 {@code StpInterfaceImpl} 本身依赖 {@code UserService},
 * 如果本类直接持有它, 就会形成
 * {@code UserServiceImpl → 本类 → StpInterfaceImpl → UserService → UserServiceImpl}。
 * 详见 {@link AuthorityChangedEvent} 的说明。
 */
@Component
@AllArgsConstructor
public class AuthorityChangeNotifier {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 某个用户的授权变了（例如给他换了一组角色）。
     * <p>
     * 只失效这一个人 —— 影响面越小, 其他用户的缓存越不容易被无谓地清掉。
     */
    public void userChanged(Long userId) {
        eventPublisher.publishEvent(AuthorityChangedEvent.of(userId));
    }

    /**
     * 影响面不确定的变更：改了角色本身（如给角色增删权限）、删了角色或权限。
     * <p>
     * 这类变更影响的是"所有持有该角色的人", 逐个算太容易漏, 直接全量失效。
     */
    public void allChanged() {
        eventPublisher.publishEvent(AuthorityChangedEvent.all());
    }
}
