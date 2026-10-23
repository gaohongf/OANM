package com.github.gaohongf.wo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.wo.entity.req.AssignWorkOrderCommand;
import com.github.gaohongf.wo.entity.req.DecideCancelWorkOrderCommand;
import com.github.gaohongf.wo.entity.req.ResolveWorkOrderCommand;
import com.github.gaohongf.wo.entity.req.SubmitWorkOrderCommand;
import com.github.gaohongf.wo.entity.res.WorkOrderCreatedRes;
import com.github.gaohongf.wo.entity.res.WorkOrderDetailRes;
import com.github.gaohongf.wo.entity.res.WorkOrderRes;
import com.github.gaohongf.wo.entity.res.WorkOrderStatsRes;
import com.github.gaohongf.wo.service.WorkOrderService;
import com.lingyun.base.rsm.GenericRsm;
import com.lingyun.base.rsm.annotation.ExecutionFailed;
import com.lingyun.base.rsm.annotation.ExecutionSuccess;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * 运维工单。
 *
 * <h2>路径里是下划线 {@code work_order}，不是连字符</h2>
 * 这不一致，但不能改：鉴权是按 {@code METHOD:路径模式} 比对权限键的，而
 * {@code GET:/api/ops/work_order/{id}} 这个键在 {@code permissions} 表里已经存在，
 * 前端菜单和 {@code <Auth code="...">} 也引用了它。改成 {@code /work-order} 会让
 * 现有授权静默失去作用（表现为 403，而权限管理界面上看不出哪里错了）。
 * 新键确实会由自注册建出来，但旧的授权关系不会跟着走 —— 所以保持原样。
 *
 * <h2>列表与详情是两个接口</h2>
 * 列表只给摘要（见 {@link WorkOrderRes}），正文由详情单独取。这不是"多此一举"：
 * {@code solution_detail} 是模型生成的正文，可能上千字，列表带出来就是成倍的
 * 无用载荷，而它只在抽屉打开时才需要。
 *
 * <h2>流转与统计的权限键</h2>
 * <ul>
 *   <li>{@code PUT:/api/ops/work_order/{id}/claim} —— 主动接取；</li>
 *   <li>{@code PUT:/api/ops/work_order/{id}/assignee} —— 派发/改派；</li>
 *   <li>{@code PUT:/api/ops/work_order/{id}/resolve} —— 结单；</li>
 *   <li>{@code PUT:/api/ops/work_order/{id}/cancel} —— 取消（没人受理时直接取消，否则发起申请）；</li>
 *   <li>{@code PUT:/api/ops/work_order/{id}/cancel/approval} —— 受理人同意/拒绝取消申请；</li>
 *   <li>{@code GET:/api/ops/work_order/stats} —— 概览统计。</li>
 * </ul>
 * 这些写端点都由启动时的接口扫描自动登记，非超级管理员角色要在角色管理里显式授权。
 * 派发候选人 = 持有接取权限的人，所以给运维授权接取之后，他们才会出现在派发下拉里。
 * 概览统计单独一个键，是为了让"能看汇总"和"能看每一单"可以分开授权。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/ops/work_order")
public class OpsWorkOrderController {

    private final WorkOrderService workOrderService;

    /**
     * 分页列表。
     * <p>
     * {@code scope} 用来支撑「我的工单」：
     * <ul>
     *   <li>{@code all}（默认）—— 全部工单；</li>
     *   <li>{@code created} —— 当前登录人提交的工单；</li>
     *   <li>{@code assigned} —— 当前登录人受理的工单。</li>
     * </ul>
     * 登录人由服务端从会话里取，不接受前端传用户 id。
     * <p>
     * 只有写操作才需要那句"创建成功"。
     */
    @GetMapping
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public PageRes<WorkOrderRes> page(
            @RequestParam(name = "current", defaultValue = "1") long current,
            @RequestParam(name = "size", defaultValue = "10") long size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "scope", defaultValue = "all") String scope) {
        return workOrderService.page(current, size, keyword, status, scope);
    }

    /**
     * 概览统计。
     * <p>
     * 路径是固定的 {@code /stats}，比下面的 {@code /{id}} 更具体，Spring 会优先匹配它；
     * 不会因为 {@code /{id}} 先声明就被当成 id 解析。
     *
     * @param days 趋势窗口，只认 7 / 30，其它值按 7 天处理
     */
    @GetMapping("/stats")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public WorkOrderStatsRes stats(@RequestParam(name = "days", defaultValue = "7") int days) {
        return workOrderService.stats(days);
    }

    /** 详情。 */
    @GetMapping("/{id}")
    @ExecutionSuccess(GenericRsm.QUERY_SUCCESS)
    @ExecutionFailed(GenericRsm.QUERY_FAILED)
    public WorkOrderDetailRes detail(@PathVariable("id") Long id) {
        return workOrderService.findDetail(id);
    }

    /**
     * 建单。
     * <p>
     * 入参里没有状态，新单固定是 {@code NEW}；也没有受理人，那是受理环节的事。
     * AI 对话的归档信息（{@code aiConversationId} / {@code aiSelectedOptionId}）由前端提交时带上，
     * 手工建单则为空。
     */
    @ExecutionSuccess(GenericRsm.CREATE_SUCCESS)
    @ExecutionFailed(GenericRsm.CREATE_FAILED)
    @PostMapping
    public WorkOrderCreatedRes create(@RequestBody @Valid SubmitWorkOrderCommand command) {
        return new WorkOrderCreatedRes(String.valueOf(workOrderService.create(command)));
    }

    /**
     * 主动接取。
     * <p>
     * 登录人由服务端从会话里取，入参里没有"谁接的" —— 否则任何人都能替别人接单。
     */
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{id}/claim")
    public void claim(@PathVariable("id") Long id) {
        workOrderService.claim(id);
    }

    /**
     * 派发 / 改派。
     * <p>
     * 目标必须存在、未封禁且持有接取权限；已受理的工单可以改派，但已解决/已关闭的不行。
     * 派发成功后会给受理人发一条站内信（派给自己时不发）。
     */
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{id}/assignee")
    public void assign(@PathVariable("id") Long id,
                       @RequestBody @Valid AssignWorkOrderCommand command) {
        workOrderService.assign(id, command);
    }

    /**
     * 结单。
     * <p>
     * 只有当前受理人可以把已受理的工单推进到「已解决」。请求体可以省略 —— 心得是选填的，
     * 前端不填时不必为了一个空字段硬造一个 JSON 对象。
     * <p>
     * 工单上如果有待确认的取消申请，这里会被拒绝：先处理取消，避免结单和取消同时发生。
     */
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{id}/resolve")
    public void resolve(@PathVariable("id") Long id,
                        @RequestBody(required = false) @Valid ResolveWorkOrderCommand command) {
        workOrderService.resolve(id, command);
    }

    /**
     * 取消工单，只能由提单人发起。
     * <p>
     * 没人受理（待受理、没有受理人）时直接关闭；已被受理时只写入取消申请并通知
     * 当前受理人，状态仍是「已受理」，等受理人通过 {@code /cancel/approval} 处理。
     * <p>
     * 不提供请求体：取消的发起人由登录态决定，前端传不了、也不需要传。
     */
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{id}/cancel")
    public void cancel(@PathVariable("id") Long id) {
        workOrderService.cancel(id);
    }

    /**
     * 处理取消申请，只能由当前受理人调用。
     * <p>
     * {@code approved=true} 同意并关闭工单（状态变为 {@code CLOSED}）；{@code false} 拒绝并清空申请时间。
     * 无论哪种结果都会给提单人发一条站内信。
     */
    @ExecutionSuccess(GenericRsm.UPDATE_SUCCESS)
    @ExecutionFailed(GenericRsm.UPDATE_FAILED)
    @PutMapping("/{id}/cancel/approval")
    public void decideCancel(@PathVariable("id") Long id,
                             @RequestBody DecideCancelWorkOrderCommand command) {
        workOrderService.decideCancel(id, command);
    }
}
