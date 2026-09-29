package com.github.gaohongf.auth.mq.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务把自己"需要权限的端点"上报给 service-auth。
 *
 * <h2>为什么用消息而不是直接调接口</h2>
 * 上报这件事<b>丢不起</b>：漏一个端点，那个端点就永远不会有一条权限行，
 * 于是<b>任何角色都不可能被授予它</b>，所有人都访问不了 —— 而现象只是 403，
 * 看不出跟"登记"有关。
 * <p>
 * 服务启动时 service-auth 可能正好不可用。走 HTTP 的话这次上报直接失败（要么丢，
 * 要么得自己实现重试）；走 Kafka 则消息会留在 broker 上，等 service-auth 起来后再消费。
 * 两种传输的取舍见 {@code ServiceRegistrar}。
 *
 * <p>此外还保留了一个 HTTP 补报端点（{@code POST /auth/internal/register}），
 * 用于消息已经过了 retention、需要手动补一次的场景。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public final class RegisterServiceAuth {

    /**
     * 上报方的实例标识（服务名 + 实例地址）。
     * <p>
     * 不参与入库（同一批端点从哪个实例上报效果一样），只用于日志 ——
     * 排查"某个端点是谁登记的"时需要有个人能问。
     */
    private String instanceId;

    /**
     * 权限键列表，形如 {@code GET:/api/ops/work_order/{id}}。
     * <p>
     * 与鉴权拦截器推导权限键的方式完全一致，所以上报的内容可以直接入库成
     * {@code permissions.permission_key}，不需要任何转换。
     */
    private List<String> auths;
}
