package com.github.gaohongf.wo.service;

import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.wo.entity.req.AssignWorkOrderCommand;
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
     * @param mine    true 时只看当前登录人名下的工单。登录人由服务端取，不接受调用方指定
     */
    PageRes<WorkOrderRes> page(long current, long size, String keyword, String status, boolean mine);

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
     * 概览统计。
     *
     * @param days 趋势窗口，只认 7 和 30；其它值按 7 天处理
     */
    WorkOrderStatsRes stats(int days);
}
