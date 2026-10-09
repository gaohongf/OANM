package com.github.gaohongf.wo.service.impl;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.wo.dao.WorkOrderDao;
import com.github.gaohongf.wo.entity.po.WorkOrderEntity;
import com.github.gaohongf.wo.entity.po.WorkOrderStatus;
import com.github.gaohongf.wo.entity.req.SubmitWorkOrderCommand;
import com.github.gaohongf.wo.entity.res.WorkOrderDetailRes;
import com.github.gaohongf.wo.entity.res.WorkOrderRes;
import com.github.gaohongf.wo.mapstruct.WorkOrderConverter;
import com.github.gaohongf.wo.rsm.WorkOrderRsm;
import com.github.gaohongf.wo.service.WorkOrderService;
import com.lingyun.base.rsm.R;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class WorkOrderServiceImpl implements WorkOrderService {

    private final WorkOrderDao workOrderDao;
    private final WorkOrderConverter workOrderConverter;

    @Override
    public PageRes<WorkOrderRes> page(long current, long size, String keyword, String status) {
        long safeCurrent = PageRes.clampCurrent(current);
        long safeSize = PageRes.clampSize(size);
        WorkOrderStatus statusValue = parseStatus(status);

        LambdaQueryWrapper<WorkOrderEntity> wrapper = Wrappers.<WorkOrderEntity>lambdaQuery()
                .eq(statusValue != null, WorkOrderEntity::getStatus, statusValue)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(WorkOrderEntity::getTitle, keyword)
                        .or()
                        .like(WorkOrderEntity::getProblemDescription, keyword))
                // 新的在前 —— 与 PermissionServiceImpl 的升序相反, 这是有意的:
                // 权限列表是"查某一行"（顺序无所谓, 求稳定）, 工单列表是"看最近发生了什么",
                // 升序会把刚建的工单压到最后几页。加 id 是为了 create_time 相同时有确定顺序
                // （同一秒批量插入时, 没有这个次级键翻页会出现重复/漏行）。
                .orderByDesc(WorkOrderEntity::getCreateTime)
                .orderByDesc(WorkOrderEntity::getId);

        IPage<WorkOrderEntity> page = workOrderDao.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(),
                workOrderConverter.toResList(page.getRecords()));
    }

    @Override
    public Long create(SubmitWorkOrderCommand command) {
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.setTitle(command.getTitle().trim());
        // 解析失败会直接抛业务错误("工单类型只能是 demand 或 fault"),
        // 而不是让一个非法的枚举值走到插入语句上变成 500。
        entity.setType(command.toType());
        entity.setPriority(command.toPriority());
        entity.setProblemDescription(command.getProblemDescription().trim());
        entity.setOriginalProblemDescription(normalizeRounds(command.getOriginalProblemDescription()));
        entity.setSolutionDetail(trimToNull(command.getSolutionDetail()));
        entity.setAiConversationId(trimToNull(command.getAiConversationId()));
        entity.setAiSelectedOptionId(command.getAiSelectedOptionId());
        // 状态由服务端定, 入参里没有这个字段 —— 见 WorkOrderStatus 的说明
        entity.setStatus(WorkOrderStatus.NEW);

        // ASSIGN_ID 在插入前就把雪花 id 填回 entity, 所以插入后能直接取
        workOrderDao.insert(entity);
        return entity.getId();
    }

    @Override
    public WorkOrderDetailRes findDetail(Long id) {
        WorkOrderEntity entity = workOrderDao.selectById(id);
        if (entity == null) {
            // selectById 自带逻辑删除条件, 所以"已删除"和"从未存在"走到这里是同一个分支 ——
            // 对调用方而言也确实是同一件事: 查不到
            R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
        }
        return workOrderConverter.toDetailRes(entity);
    }

    /**
     * 把筛选参数转成枚举。空/空白表示"不筛选"，非空但解析不出则拒绝。
     */
    private static WorkOrderStatus parseStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        // 独立语句的原因同 SubmitWorkOrderCommand#toType: R.error 是 <T> T,
        // 塞进 orElseThrow 会被推成 Throwable 而要求声明 throws
        Optional<WorkOrderStatus> parsed = WorkOrderStatus.parse(raw);
        if (parsed.isEmpty()) {
            R.error(WorkOrderRsm.WORK_ORDER_STATUS_INVALID);
        }
        return parsed.get();
    }

    /**
     * 把用户原始描述规整成"一行一轮"。
     *
     * <p>
     * 这个不变量必须在这里建立：详情页拿到它就直接按 {@code \n} 拆行，一行当一轮展示。
     * 如果库里存着空行，界面上就多出一轮空白的"用户补充"。
     *
     * <p>
     * 全空时返回 {@code null} 而不是空串 —— "没有原始描述"只该有一种表示，
     * 否则前端要同时判 {@code null}、{@code ''} 和 {@code '\n'} 三种情况。
     */
    private static String normalizeRounds(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String joined = Arrays.stream(text.split("\n"))
                .map(String::trim)
                // 兼容 CRLF: 按 \n 切完每行尾部还挂着 \r, 上面的 trim 已经处理掉了
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining("\n"));
        return joined.isEmpty() ? null : joined;
    }

    /** 空白串一律归一成 null，避免同一个"没填"在库里有两种存法。 */
    private static String trimToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
