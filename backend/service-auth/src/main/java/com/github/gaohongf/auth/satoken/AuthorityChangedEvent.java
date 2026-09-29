package com.github.gaohongf.auth.satoken;

/**
 * 授权数据发生了变化。
 *
 * <h2>为什么要发事件而不是直接调失效方法</h2>
 * 直觉做法是让改数据的 Service 直接注入"失效器"并调它, 但那会形成环:
 * <pre>
 *   UserServiceImpl → 失效器 → StpInterfaceImpl → UserService → UserServiceImpl
 * </pre>
 * 根因是:<b>失效缓存的目标本身依赖于被改的那些 Service</b>（{@code StpInterfaceImpl}
 * 要调 {@code UserService.findAuthorities} 才能拿到一个人的授权）。
 * 把这个"副作用"从数据服务里挪出去、改成事件, 环就不存在了 ——
 * 数据服务只依赖 {@code ApplicationEventPublisher} 这个框架 bean。
 *
 * <h2>顺带得到的两个好处</h2>
 * <ol>
 *   <li>失效时机由监听方用 {@code @TransactionalEventListener(AFTER_COMMIT)} 声明,
 *       不用手写 {@code TransactionSynchronization}。这一点很重要: 在事务<b>提交前</b>
 *       失效缓存, 会让另一个线程正好读到未提交的旧数据并把它重新缓存 30 秒。</li>
 *   <li>将来要加"广播给其他服务"时, 只加一个新的监听者, 不动任何数据服务。</li>
 * </ol>
 *
 * @param userId 受影响的用户; {@code null} 表示"影响面不确定, 全量失效"
 *               （如改了角色本身的权限、删了角色或权限 —— 这些影响所有持有者）
 */
public record AuthorityChangedEvent(Long userId) {

    /** 全量失效: 影响面不确定的变更 */
    public static AuthorityChangedEvent all() {
        return new AuthorityChangedEvent(null);
    }

    /** 只失效某个用户 */
    public static AuthorityChangedEvent of(Long userId) {
        return new AuthorityChangedEvent(userId);
    }

    public boolean isAll() {
        return userId == null;
    }
}
