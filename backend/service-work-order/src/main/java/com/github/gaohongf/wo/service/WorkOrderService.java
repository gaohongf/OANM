package com.github.gaohongf.wo.service;

import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.wo.entity.req.SubmitWorkOrderCommand;
import com.github.gaohongf.wo.entity.res.WorkOrderDetailRes;
import com.github.gaohongf.wo.entity.res.WorkOrderRes;

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
     */
    PageRes<WorkOrderRes> page(long current, long size, String keyword, String status);

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
}
