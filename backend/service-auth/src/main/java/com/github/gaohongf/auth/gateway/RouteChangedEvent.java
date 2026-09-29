package com.github.gaohongf.auth.gateway;

/**
 * 网关路由发生了变化。
 *
 * <h2>为什么用事件而不是直接调广播</h2>
 * 广播必须发生在事务<b>提交之后</b>: 如果在提交前就通知网关, 网关会立刻回源拉取 ——
 * 而那时新路由还没提交, 它会拉到旧的并缓存起来, 直到下一次有人改路由才纠正。
 * 用事件 + {@code @TransactionalEventListener(AFTER_COMMIT)} 表达这个时序,
 * 比在每个写方法里手写 {@code TransactionSynchronization} 可靠得多。
 *
 * @param routeId 受影响的路由 id, 仅用于日志; 版本号本身与具体哪条路由无关
 */
public record RouteChangedEvent(String routeId) {
}
