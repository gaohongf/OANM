package com.github.gaohongf.wo.rsm;

import org.springframework.http.HttpStatus;

import com.lingyun.base.rsm.RsmManager;
import com.lingyun.base.rsm.annotation.RsmInfo;

/**
 * 工单相关的统一响应消息。
 *
 * <h2>不要给它加 @Component</h2>
 * 它由 {@code com.github.gaohongf.wo.config.WorkOrderRsmConfiguration} 注册。
 * <p>
 * {@code AuthRsm} 那边踩过一次这个坑，值得在这里重述，因为症状和原因都反直觉：
 * 一个没注册过的消息键，RSM 解析不出模板就退回默认消息，于是"工单不存在"会被渲染成
 * {@code {"code":1016,"msg":"成功"}} —— <b>拒绝被报成了成功</b>，而且不抛错、不告警。
 */
public class WorkOrderRsm implements RsmManager {

    /** 按 id 查工单但不存在（已删除或从未创建） */
    @RsmInfo(template = "工单不存在", status = HttpStatus.NOT_FOUND)
    public static final String WORK_ORDER_NOT_FOUND = "WorkOrder_WORK_ORDER_NOT_FOUND";

    /**
     * 工单类型不是 demand / fault。
     * <p>
     * 文案里把合法值列出来，是因为这个错误最常见的来源是模型输出的漂移 ——
     * 调用方看到"只能是 demand 或 fault"就知道该往哪儿改，而"参数非法"提供不了这个信息。
     */
    @RsmInfo(template = "工单类型只能是 demand 或 fault", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_TYPE_INVALID = "WorkOrder_WORK_ORDER_TYPE_INVALID";

    /** 优先级不是 low / medium / high / urgent */
    @RsmInfo(template = "工单优先级只能是 low、medium、high 或 urgent", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_PRIORITY_INVALID = "WorkOrder_WORK_ORDER_PRIORITY_INVALID";

    /**
     * 列表筛选传了不认识的状态。
     * <p>
     * 这里拒绝而不是忽略，是因为"忽略"的表现是<b>返回全部工单</b> —— 界面上看起来
     * 筛选生效了，实际上没有。这种"看起来对"的错误比一个 400 难查得多。
     */
    @RsmInfo(template = "工单状态只能是 NEW、ACCEPTED、RESOLVED 或 CLOSED", status = HttpStatus.BAD_REQUEST)
    public static final String WORK_ORDER_STATUS_INVALID = "WorkOrder_WORK_ORDER_STATUS_INVALID";
}
