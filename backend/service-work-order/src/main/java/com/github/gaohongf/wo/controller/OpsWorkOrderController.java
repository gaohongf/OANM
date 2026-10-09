package com.github.gaohongf.wo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.wo.entity.req.SubmitWorkOrderCommand;
import com.github.gaohongf.wo.entity.res.WorkOrderCreatedRes;
import com.github.gaohongf.wo.entity.res.WorkOrderDetailRes;
import com.github.gaohongf.wo.entity.res.WorkOrderRes;
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
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/ops/work_order")
public class OpsWorkOrderController {

    private final WorkOrderService workOrderService;

    /**
     * 分页列表。
     * <p>
     * 查询方法不标 {@code @ExecutionSuccess} —— 照 {@code RoleController.list} 的既有约定，
     * 只有写操作才需要那句"创建成功"。
     */
    @GetMapping
    public PageRes<WorkOrderRes> page(
            @RequestParam(name = "current", defaultValue = "1") long current,
            @RequestParam(name = "size", defaultValue = "10") long size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status) {
        return workOrderService.page(current, size, keyword, status);
    }

    /** 详情。 */
    @GetMapping("/{id}")
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
}
