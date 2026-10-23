package com.github.gaohongf.wo.service;

import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.wo.entity.req.AssignWorkOrderCommand;
import com.github.gaohongf.wo.entity.req.DecideCancelWorkOrderCommand;
import com.github.gaohongf.wo.entity.req.ResolveWorkOrderCommand;
import com.github.gaohongf.wo.entity.req.SubmitWorkOrderCommand;
import com.github.gaohongf.wo.entity.res.WorkOrderDetailRes;
import com.github.gaohongf.wo.entity.res.WorkOrderRes;
import com.github.gaohongf.wo.entity.res.WorkOrderStatsRes;

/**
 * 工单。
 */
public interface WorkOrderService {

    /**
     * 分页查询。
     *
     * @param keyword 按标题 / 工单描述模糊匹配，可为空
     * @param status  精确筛选状态，可为空（表示不筛选）。
     *                传了但解析不出合法状态时抛业务错误，而不是当作"不筛选" —— 见
     *                {@code WorkOrderRsm.WORK_ORDER_STATUS_INVALID} 的说明
     * @param scope   列表范围：{@code all} / {@code created}（当前登录人提交的）/
     *                {@code assigned}（当前登录人受理的）。登录人由服务端取，不接受调用方指定；
     *                其它值按参数错误拒绝，而不是退化成"全部"
     */
    PageRes<WorkOrderRes> page(long current, long size, String keyword, String status, String scope);

    /**
     * 建单。
     *
     * @return 新工单 id
     */
    Long create(SubmitWorkOrderCommand command);

    /**
     * 详情。
     *
     * @throws com.lingyun.base.rsm.exception.RequestException 工单不存在时
     */
    WorkOrderDetailRes findDetail(Long id);

    /**
     * 主动接取：把当前登录人写成受理人，状态变为已受理。
     * <p>
     * 只有 {@code NEW} 且尚未派发的工单可以接取；并发接取时只有一个请求会成功。
     *
     * @throws com.lingyun.base.rsm.exception.RequestException 工单不存在、状态不可接取，或已被他人接取
     */
    void claim(Long id);

    /**
     * 派发：把工单指定给目标用户（可对已受理的工单改派）。
     * <p>
     * 目标必须存在、未被封禁，并且持有接单权限 —— 否则派过去的单子他自己也处理不了。
     *
     * @throws com.lingyun.base.rsm.exception.RequestException 工单不存在、状态不可派发、
     *         受理人不合法，或校验受理人权限时依赖不可用
     */
    void assign(Long id, AssignWorkOrderCommand command);

    /**
     * 结单：受理人把已受理的工单推进到 {@code RESOLVED}。
     * <p>
     * 只有当前受理人可以操作；{@code note} 是选填的本次心得，写入
     * {@code assignee_note}。工单上如果有待确认的取消申请，先拒绝这次结单。
     *
     * @throws com.lingyun.base.rsm.exception.RequestException 工单不存在、状态不可结单、
     *         操作人不是受理人，或存在待确认的取消申请
     */
    void resolve(Long id, ResolveWorkOrderCommand command);

    /**
     * 取消工单（只能由提单人发起）。
     * <p>
     * 没人受理的工单直接关闭（状态改为 {@code CLOSED}）；已受理的工单先写入取消申请
     * 并通知受理人，状态仍是 {@code ACCEPTED}，等受理人处理后才会关闭。
     *
     * @throws com.lingyun.base.rsm.exception.RequestException 工单不存在、操作人不是提单人、
     *         状态不允许取消，或并发修改冲突
     */
    void cancel(Long id);

    /**
     * 受理人处理取消申请。
     *
     * @param command {@code approved=true} 同意并关闭工单；{@code false} 拒绝并清空申请
     * @throws com.lingyun.base.rsm.exception.RequestException 工单不存在、没有待处理的申请、
     *         操作人不是当前受理人，或并发修改冲突
     */
    void decideCancel(Long id, DecideCancelWorkOrderCommand command);

    /**
     * 概览统计。
     *
     * @param days 趋势窗口，只认 7 和 30；其它值按 7 天处理
     */
    WorkOrderStatsRes stats(int days);
}
