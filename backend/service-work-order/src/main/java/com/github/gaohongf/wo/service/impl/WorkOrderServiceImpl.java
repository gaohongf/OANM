package com.github.gaohongf.wo.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.gaohongf.auth.client.ApiResponse;
import com.github.gaohongf.auth.client.UserClient;
import com.github.gaohongf.auth.resolve.UserLookup;
import com.github.gaohongf.auth.res.UserAuthorities;
import com.github.gaohongf.auth.res.UserRes;
import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.mybatis.AuditMetaObjectHandler;
import com.github.gaohongf.wo.dao.WorkOrderDao;
import com.github.gaohongf.wo.entity.po.WorkOrderEntity;
import com.github.gaohongf.wo.entity.po.WorkOrderStatus;
import com.github.gaohongf.wo.entity.req.AssignWorkOrderCommand;
import com.github.gaohongf.wo.entity.req.DecideCancelWorkOrderCommand;
import com.github.gaohongf.wo.entity.req.ResolveWorkOrderCommand;
import com.github.gaohongf.wo.entity.req.SubmitWorkOrderCommand;
import com.github.gaohongf.wo.entity.res.DailyCountRes;
import com.github.gaohongf.wo.entity.res.RecentWorkOrderRes;
import com.github.gaohongf.wo.entity.res.WorkOrderDetailRes;
import com.github.gaohongf.wo.entity.res.WorkOrderRes;
import com.github.gaohongf.wo.entity.res.WorkOrderStatsRes;
import com.github.gaohongf.wo.mapstruct.WorkOrderConverter;
import com.github.gaohongf.wo.mq.WorkOrderAssignedEvent;
import com.github.gaohongf.wo.mq.WorkOrderCancelDecidedEvent;
import com.github.gaohongf.wo.mq.WorkOrderCancelRequestedEvent;
import com.github.gaohongf.wo.mq.WorkOrderCreatedEvent;
import com.github.gaohongf.wo.rsm.WorkOrderRsm;
import com.github.gaohongf.wo.service.WorkOrderService;
import com.lingyun.base.rsm.R;

import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
@Service
public class WorkOrderServiceImpl implements WorkOrderService {

    /**
     * 接取工单接口的权限键。
     * <p>
     * 派发候选人就是"持有这个键（或 {@code *}）的人"。写成常量而不是散在比较里：
     * 它必须与 {@code OpsWorkOrderController} 上的路径逐字一致，而两处一旦漂移，
     * 症状是"候选人列表看着正常、派发却被拒绝"，很难一眼定位。
     */
    private static final String CLAIM_PERMISSION = "PUT:/api/ops/work_order/{id}/claim";

    /** 概览"最近动态"的条数。再多就不是"最近"了，而是第二个列表页 */
    private static final int RECENT_LIMIT = 6;

    /** 单条图片地址的长度上限：正常上传地址远小于它，这里只防异常长的 query。 */
    private static final int MAX_IMAGE_URL_LENGTH = 512;

    private final WorkOrderDao workOrderDao;
    private final WorkOrderConverter workOrderConverter;
    private final ApplicationEventPublisher eventPublisher;
    private final UserLookup userLookup;
    private final UserClient userClient;

    @Override
    public PageRes<WorkOrderRes> page(long current, long size, String keyword, String status, String scope) {
        long safeCurrent = PageRes.clampCurrent(current);
        long safeSize = PageRes.clampSize(size);
        WorkOrderStatus statusValue = parseStatus(status);
        ListScope listScope = parseScope(scope);

        LambdaQueryWrapper<WorkOrderEntity> wrapper = Wrappers.<WorkOrderEntity>lambdaQuery()
                .eq(statusValue != null, WorkOrderEntity::getStatus, statusValue)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(WorkOrderEntity::getTitle, keyword)
                        .or()
                        .like(WorkOrderEntity::getProblemDescription, keyword));

        if (listScope != null) {
            // 登录人由服务端取, 不接受前端传 user id —— 否则"我的工单"可以变成"查别人的工单"。
            // 只在真的要按人过滤时才取登录态, all 范围不会白调一次 StpUtil。
            Long userId = StpUtil.getLoginIdAsLong();
            if (listScope == ListScope.CREATED) {
                wrapper.eq(WorkOrderEntity::getCreateBy, userId);
            } else {
                wrapper.eq(WorkOrderEntity::getAssigneeId, userId);
            }
        }

        // 新的在前 —— 与 PermissionServiceImpl 的升序相反, 这是有意的:
        // 权限列表是"查某一行"（顺序无所谓, 求稳定）, 工单列表是"看最近发生了什么",
        // 升序会把刚建的工单压到最后几页。加 id 是为了 create_time 相同时有确定顺序
        // （同一秒批量插入时, 没有这个次级键翻页会出现重复/漏行）。
        wrapper.orderByDesc(WorkOrderEntity::getCreateTime)
                .orderByDesc(WorkOrderEntity::getId);

        IPage<WorkOrderEntity> page = workOrderDao.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(),
                workOrderConverter.toResList(page.getRecords()));
    }

    @Override
    public void claim(Long id) {
        Long userId = StpUtil.getLoginIdAsLong();

        WorkOrderEntity current = workOrderDao.selectById(id);
        if (current == null) {
            R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
        }
        if (current.getStatus() != WorkOrderStatus.NEW || current.getAssigneeId() != null) {
            R.error(WorkOrderRsm.WORK_ORDER_NOT_CLAIMABLE);
        }

        WorkOrderEntity patch = new WorkOrderEntity();
        // 必须带上读到的版本号: 为 null 时乐观锁拦截器会直接跳过, 并发的派发就能覆盖这次接取。
        patch.setVersion(current.getVersion() == null ? 0 : current.getVersion());
        patch.setAssigneeId(userId);
        patch.setStatus(WorkOrderStatus.ACCEPTED);

        // 状态/受理人条件与版本条件一起构成原子更新: 两个人同时接同一单时只有一个能落到 1 行。
        int rows = workOrderDao.update(patch, Wrappers.<WorkOrderEntity>lambdaUpdate()
                .eq(WorkOrderEntity::getId, id)
                .eq(WorkOrderEntity::getStatus, WorkOrderStatus.NEW)
                .isNull(WorkOrderEntity::getAssigneeId));
        if (rows == 0) {
            R.error(WorkOrderRsm.WORK_ORDER_ALREADY_CLAIMED);
        }
    }

    @Override
    public void assign(Long id, AssignWorkOrderCommand command) {
        Long assigneeId = command.toAssigneeId();
        Long operatorId = StpUtil.getLoginIdAsLong();

        WorkOrderEntity current = workOrderDao.selectById(id);
        if (current == null) {
            R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
        }
        WorkOrderStatus status = current.getStatus();
        if (status != WorkOrderStatus.NEW && status != WorkOrderStatus.ACCEPTED) {
            R.error(WorkOrderRsm.WORK_ORDER_NOT_ASSIGNABLE);
        }
        // 已经在这个人名下, 再派一次没有任何变化 —— 不改库, 也不会多发一条通知。
        if (status == WorkOrderStatus.ACCEPTED && assigneeId.equals(current.getAssigneeId())) {
            return;
        }
        // 取消申请是发给当前受理人的，换人之后这次申请就没有明确的处理人了。
        // 要么由当前受理人先同意/拒绝，要么由管理员撤掉申请后再改派（当前没有撤掉入口）。
        if (current.getCancelRequestedAt() != null) {
            R.error(WorkOrderRsm.WORK_ORDER_CANCEL_REQUEST_PENDING);
        }

        // 远程校验放在更新之前、事务之外: 失败时库里什么都没动, 重试是安全的。
        validateAssignee(assigneeId);

        int currentVersion = current.getVersion() == null ? 0 : current.getVersion();
        WorkOrderEntity patch = new WorkOrderEntity();
        patch.setId(id);
        patch.setVersion(currentVersion);
        patch.setAssigneeId(assigneeId);
        patch.setStatus(WorkOrderStatus.ACCEPTED);

        int rows = workOrderDao.updateById(patch);
        if (rows == 0) {
            // 版本冲突或者单据刚好被删。重查一次, 把两种结果分开报, 免得让人对着
            // "已被修改"去刷新一个已经不存在的工单。
            if (workOrderDao.selectById(id) == null) {
                R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
            }
            R.error(WorkOrderRsm.WORK_ORDER_CONCURRENT_MODIFIED);
        }

        // 版本号不等拦截器回写实体, 直接用"读到的版本 + 1": 与乐观锁的成功条件完全等价。
        eventPublisher.publishEvent(new WorkOrderAssignedEvent(id, current.getTitle(),
                current.getType(), assigneeId, operatorId, currentVersion + 1));
    }

    @Override
    public void resolve(Long id, ResolveWorkOrderCommand command) {
        Long userId = StpUtil.getLoginIdAsLong();

        WorkOrderEntity current = workOrderDao.selectById(id);
        if (current == null) {
            R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
        }
        assertResolvable(current, userId);

        int currentVersion = current.getVersion() == null ? 0 : current.getVersion();
        WorkOrderEntity patch = new WorkOrderEntity();
        // 同 claim/assign: 版本为 null 时乐观锁拦截器会跳过, 条件更新就不再是原子的
        patch.setVersion(currentVersion);
        patch.setStatus(WorkOrderStatus.RESOLVED);
        // 空心得归一成 null; 非空才进入 UPDATE 的 SET 子句
        patch.setAssigneeNote(trimToNull(command == null ? null : command.getNote()));

        // 状态/受理人/取消申请三个条件与版本条件一起构成原子更新:
        // 在读和写之间被改派、被结单、或被发起取消申请, 都会落到 0 行。
        int rows = workOrderDao.update(patch, Wrappers.<WorkOrderEntity>lambdaUpdate()
                .eq(WorkOrderEntity::getId, id)
                .eq(WorkOrderEntity::getStatus, WorkOrderStatus.ACCEPTED)
                .eq(WorkOrderEntity::getAssigneeId, userId)
                .isNull(WorkOrderEntity::getCancelRequestedAt));
        if (rows == 0) {
            WorkOrderEntity latest = workOrderDao.selectById(id);
            if (latest == null) {
                R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
            }
            // 重查一次, 把"状态变了 / 换人了 / 来了取消申请 / 只是版本冲突"分开报,
            // 而不是让所有并发情况都显示一句"请刷新"。
            assertResolvable(latest, userId);
            R.error(WorkOrderRsm.WORK_ORDER_CONCURRENT_MODIFIED);
        }
    }

    /**
     * 结单的前置条件。读后写失败时会用最新快照再跑一次，所以这里只依赖传入的快照。
     */
    private static void assertResolvable(WorkOrderEntity order, Long operatorId) {
        if (order.getStatus() != WorkOrderStatus.ACCEPTED) {
            R.error(WorkOrderRsm.WORK_ORDER_NOT_RESOLVABLE);
        }
        if (!operatorId.equals(order.getAssigneeId())) {
            R.error(WorkOrderRsm.WORK_ORDER_RESOLVE_FORBIDDEN);
        }
        if (order.getCancelRequestedAt() != null) {
            R.error(WorkOrderRsm.WORK_ORDER_CANCEL_REQUEST_PENDING);
        }
    }

    @Override
    public void cancel(Long id) {
        Long userId = StpUtil.getLoginIdAsLong();

        WorkOrderEntity current = workOrderDao.selectById(id);
        if (current == null) {
            R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
        }
        // 取消是提单人的权利, 不是管理员的权利: 管理员要处理别人的单子, 应当走派发/结单,
        // 而不是替提单人把单子关掉。
        if (!userId.equals(current.getCreateBy())) {
            R.error(WorkOrderRsm.WORK_ORDER_CANCEL_FORBIDDEN);
        }

        WorkOrderStatus status = current.getStatus();
        if (status != WorkOrderStatus.NEW && status != WorkOrderStatus.ACCEPTED) {
            R.error(WorkOrderRsm.WORK_ORDER_NOT_CANCELABLE);
        }

        int currentVersion = current.getVersion() == null ? 0 : current.getVersion();

        // 没人受理, 或者提单人自己就是受理人: 不需要别人同意, 直接关闭。
        // ACCEPTED 却没有受理人是历史脏数据, 也按"没人受理"处理, 否则这张单会卡在
        // 一个既不能结单、也没有人能同意取消的状态。
        if (status == WorkOrderStatus.NEW
                || current.getAssigneeId() == null
                || userId.equals(current.getAssigneeId())) {
            closeDirectly(id, current, userId, currentVersion);
            return;
        }
        // 已经申请过: 幂等返回。重复点击不应该产生第二条通知, 也不应该报错。
        if (current.getCancelRequestedAt() != null) {
            return;
        }

        WorkOrderEntity patch = new WorkOrderEntity();
        patch.setVersion(currentVersion);
        patch.setCancelRequestedAt(LocalDateTime.now());

        // 条件更新: 只有"仍由同一个受理人持有、且还没有取消申请"时才写入。
        // 这样两个人同时申请时只有一个人能落库, 也就只发一条通知。
        int rows = workOrderDao.update(patch, Wrappers.<WorkOrderEntity>lambdaUpdate()
                .eq(WorkOrderEntity::getId, id)
                .eq(WorkOrderEntity::getStatus, WorkOrderStatus.ACCEPTED)
                .eq(WorkOrderEntity::getAssigneeId, current.getAssigneeId())
                .isNull(WorkOrderEntity::getCancelRequestedAt));
        if (rows == 0) {
            WorkOrderEntity latest = workOrderDao.selectById(id);
            if (latest == null) {
                R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
            }
            // 并发请求已经写入了同一条申请, 对调用方来说仍然是成功。
            if (latest.getCancelRequestedAt() != null) {
                return;
            }
            R.error(WorkOrderRsm.WORK_ORDER_CONCURRENT_MODIFIED);
        }

        eventPublisher.publishEvent(new WorkOrderCancelRequestedEvent(id, current.getTitle(),
                userId, current.getAssigneeId(), currentVersion + 1));
    }

    @Override
    public void decideCancel(Long id, DecideCancelWorkOrderCommand command) {
        if (command == null || command.getApproved() == null) {
            R.error(WorkOrderRsm.WORK_ORDER_CANCEL_DECISION_INVALID);
        }
        boolean approved = command.getApproved();
        Long userId = StpUtil.getLoginIdAsLong();

        WorkOrderEntity current = workOrderDao.selectById(id);
        if (current == null) {
            R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
        }
        if (current.getStatus() != WorkOrderStatus.ACCEPTED || current.getCancelRequestedAt() == null) {
            R.error(WorkOrderRsm.WORK_ORDER_CANCEL_REQUEST_ABSENT);
        }
        if (!userId.equals(current.getAssigneeId())) {
            R.error(WorkOrderRsm.WORK_ORDER_CANCEL_DECISION_FORBIDDEN);
        }

        int currentVersion = current.getVersion() == null ? 0 : current.getVersion();
        if (approved) {
            // 同意 = 状态切到已关闭，并清掉申请时间。清时间是为了让已关闭的单子
            // 不再带着"取消待确认"的标记；null 只能通过 wrapper 的 SET 写入。
            int rows = workOrderDao.update(null, Wrappers.<WorkOrderEntity>lambdaUpdate()
                    .eq(WorkOrderEntity::getId, id)
                    .eq(WorkOrderEntity::getStatus, WorkOrderStatus.ACCEPTED)
                    .eq(WorkOrderEntity::getAssigneeId, userId)
                    .isNotNull(WorkOrderEntity::getCancelRequestedAt)
                    .eq(WorkOrderEntity::getVersion, currentVersion)
                    .set(WorkOrderEntity::getStatus, WorkOrderStatus.CLOSED)
                    .set(WorkOrderEntity::getCancelRequestedAt, null)
                    .set(WorkOrderEntity::getUpdateBy, userId)
                    .set(WorkOrderEntity::getUpdateTime, LocalDateTime.now())
                    .set(WorkOrderEntity::getVersion, currentVersion + 1));
            if (rows == 0) {
                reportDecisionConflict(id, userId);
            }
        } else {
            // 拒绝 = 清空申请时间。这里不能用 updateById: 实体上的 null 会被
            // MyBatis-Plus 的默认字段策略忽略, 申请时间会一直挂着。
            int rows = workOrderDao.update(null, Wrappers.<WorkOrderEntity>lambdaUpdate()
                    .eq(WorkOrderEntity::getId, id)
                    .eq(WorkOrderEntity::getStatus, WorkOrderStatus.ACCEPTED)
                    .eq(WorkOrderEntity::getAssigneeId, userId)
                    .isNotNull(WorkOrderEntity::getCancelRequestedAt)
                    .eq(WorkOrderEntity::getVersion, currentVersion)
                    .set(WorkOrderEntity::getCancelRequestedAt, null)
                    .set(WorkOrderEntity::getUpdateBy, userId)
                    .set(WorkOrderEntity::getUpdateTime, LocalDateTime.now())
                    .set(WorkOrderEntity::getVersion, currentVersion + 1));
            if (rows == 0) {
                reportDecisionConflict(id, userId);
            }
        }

        eventPublisher.publishEvent(new WorkOrderCancelDecidedEvent(id, current.getTitle(),
                current.getCreateBy(), userId, approved, currentVersion + 1));
    }

    /**
     * 不需要受理人确认的取消：直接把状态切到已关闭。
     * <p>
     * 条件里带上读到的状态、受理人与版本，避免与接取/改派/结单并发时互相覆盖。
     * 用 wrapper 的 SET 而不是补丁实体，是为了让 {@code update_by}/{@code update_time}
     * 与状态在同一条 SQL 里落库。
     */
    private void closeDirectly(Long id, WorkOrderEntity current, Long operatorId, int currentVersion) {
        WorkOrderStatus status = current.getStatus();
        LambdaUpdateWrapper<WorkOrderEntity> wrapper = Wrappers.<WorkOrderEntity>lambdaUpdate()
                .eq(WorkOrderEntity::getId, id)
                .eq(WorkOrderEntity::getStatus, status)
                .eq(WorkOrderEntity::getVersion, currentVersion)
                .set(WorkOrderEntity::getStatus, WorkOrderStatus.CLOSED)
                // 已关闭的单子不该再显示“取消待确认”；正常路径下它本来就是 null。
                .set(WorkOrderEntity::getCancelRequestedAt, null)
                .set(WorkOrderEntity::getUpdateBy, operatorId)
                .set(WorkOrderEntity::getUpdateTime, LocalDateTime.now())
                .set(WorkOrderEntity::getVersion, currentVersion + 1);
        // 读到的受理人 (可能为 null) 也参与条件: 期间被人接走时这次关闭应当失败。
        if (current.getAssigneeId() == null) {
            wrapper.isNull(WorkOrderEntity::getAssigneeId);
        } else {
            wrapper.eq(WorkOrderEntity::getAssigneeId, current.getAssigneeId());
        }

        int rows = workOrderDao.update(null, wrapper);
        if (rows == 0) {
            WorkOrderEntity latest = workOrderDao.selectById(id);
            if (latest == null) {
                R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
            }
            if (latest.getStatus() != status) {
                R.error(WorkOrderRsm.WORK_ORDER_NOT_CANCELABLE);
            }
            R.error(WorkOrderRsm.WORK_ORDER_CONCURRENT_MODIFIED);
        }
    }

    /** 处理取消申请时更新失败：把"申请没了 / 换人了 / 版本冲突"分开报。 */
    private void reportDecisionConflict(Long id, Long operatorId) {
        WorkOrderEntity latest = workOrderDao.selectById(id);
        if (latest == null) {
            R.error(WorkOrderRsm.WORK_ORDER_NOT_FOUND);
        }
        if (latest.getStatus() != WorkOrderStatus.ACCEPTED || latest.getCancelRequestedAt() == null) {
            R.error(WorkOrderRsm.WORK_ORDER_CANCEL_REQUEST_ABSENT);
        }
        if (!operatorId.equals(latest.getAssigneeId())) {
            R.error(WorkOrderRsm.WORK_ORDER_CANCEL_DECISION_FORBIDDEN);
        }
        R.error(WorkOrderRsm.WORK_ORDER_CONCURRENT_MODIFIED);
    }

    /**
     * 派发前校验受理人：存在、未封禁、持有接单权限。
     * <p>
     * "问不到"（service-auth 不可用）和"没权限"必须分开报：前者重试有意义，后者没有。
     * 这是 {@link UserLookup} 用"null 表示不存在、异常表示没问到"这套契约的意义所在。
     */
    private void validateAssignee(Long assigneeId) {
        UserRes user;
        try {
            user = userLookup.findById(assigneeId);
        } catch (RuntimeException failure) {
            log.warn("校验受理人 {} 失败: 用户服务不可用", assigneeId, failure);
            R.error(WorkOrderRsm.WORK_ORDER_ASSIGNEE_VERIFY_UNAVAILABLE);
            return;
        }
        if (user == null) {
            R.error(WorkOrderRsm.WORK_ORDER_ASSIGNEE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(user.getLocked())) {
            R.error(WorkOrderRsm.WORK_ORDER_ASSIGNEE_LOCKED);
        }

        UserAuthorities authorities;
        try {
            ApiResponse<UserAuthorities> response = userClient.findAuthorities(assigneeId);
            if (response == null) {
                throw new IllegalStateException("查询用户 " + assigneeId + " 的权限失败: 响应为空");
            }
            // 后端业务失败是 HTTP 200 + type=ERROR, 不检查信封会把"内部错误"当成"没有权限"
            response.requireSuccess("查询用户 " + assigneeId + " 的权限");
            authorities = response.data();
        } catch (RuntimeException failure) {
            log.warn("校验受理人 {} 的接单权限失败: 权限服务不可用", assigneeId, failure);
            R.error(WorkOrderRsm.WORK_ORDER_ASSIGNEE_VERIFY_UNAVAILABLE);
            return;
        }

        boolean allowed = authorities != null && (authorities.permissions().contains("*")
                || authorities.permissions().contains(CLAIM_PERMISSION));
        if (!allowed) {
            R.error(WorkOrderRsm.WORK_ORDER_ASSIGNEE_HAS_NO_CLAIM_PERMISSION);
        }
    }

    @Override
    public WorkOrderStatsRes stats(int days) {
        // 只认 7 / 30: 前端只有这两个档位，传别的值多半是调用方写错了，按 7 天处理比报错有用。
        int window = days == 30 ? 30 : 7;
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        WorkOrderStatsRes result = new WorkOrderStatsRes();
        Map<String, Long> byStatus = countByStatus();
        result.setPending(byStatus.getOrDefault(WorkOrderStatus.NEW.name(), 0L));
        result.setAccepted(byStatus.getOrDefault(WorkOrderStatus.ACCEPTED.name(), 0L));
        result.setResolved(byStatus.getOrDefault(WorkOrderStatus.RESOLVED.name(), 0L));
        result.setClosed(byStatus.getOrDefault(WorkOrderStatus.CLOSED.name(), 0L));
        // 直接对分组结果求和, 而不是再查一次 COUNT(*): 两者在同一时刻可能差一行,
        // 而概览上的"总数"应当等于下面几块之和。
        result.setTotal(byStatus.values().stream().mapToLong(Long::longValue).sum());

        // 周一为一周的开始。前端文案写的是"本周", 不用"近 7 天"来冒充。
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        result.setWeekCreated(countCreated(weekStart.atStartOfDay(), null));
        result.setLastWeekCreated(countCreated(weekStart.minusWeeks(1).atStartOfDay(),
                weekStart.atStartOfDay()));

        result.setTodayCompleted(countCompleted(todayStart, null));
        result.setYesterdayCompleted(countCompleted(todayStart.minusDays(1), todayStart));

        result.setDaily(daily(today.minusDays(window - 1L), today));
        result.setRecent(recent());
        return result;
    }

    /**
     * 按状态分组计数。
     * <p>
     * 用 {@code selectMaps} 而不是四次 {@code selectCount}：这是同一个聚合，一次往返就能拿全，
     * 也保证各分块看到的是同一时刻的数据。逻辑删除条件由 MyBatis-Plus 自动拼上。
     */
    private Map<String, Long> countByStatus() {
        List<Map<String, Object>> rows = workOrderDao.selectMaps(
                Wrappers.<WorkOrderEntity>query()
                        .select("status", "COUNT(*) AS cnt")
                        .groupBy("status"));
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object status = row.get("status");
            if (status != null) {
                counts.put(String.valueOf(status), toLong(row.get("cnt")));
            }
        }
        return counts;
    }

    /** 某个时间点之后（可带右开区间）创建的工单数 */
    private long countCreated(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<WorkOrderEntity> wrapper = Wrappers.<WorkOrderEntity>lambdaQuery()
                .ge(WorkOrderEntity::getCreateTime, start);
        if (end != null) {
            wrapper.lt(WorkOrderEntity::getCreateTime, end);
        }
        return workOrderDao.selectCount(wrapper);
    }

    /**
     * 某个时间区间内"完成"的工单数。
     * <p>
     * 没有独立的完成时间字段，这里用 {@code update_time} 近似 —— 口径写在
     * {@link WorkOrderStatsRes} 的类注释里。结单会写入 {@code RESOLVED}；
     * 等补上 {@code resolved_time} 后再换成真实完成时间。
     */
    private long countCompleted(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<WorkOrderEntity> wrapper = Wrappers.<WorkOrderEntity>lambdaQuery()
                // 用字符串而不是枚举值: status 列是 varchar, 这里不需要经过枚举类型处理器
                .in(WorkOrderEntity::getStatus,
                        WorkOrderStatus.RESOLVED.name(), WorkOrderStatus.CLOSED.name())
                .ge(WorkOrderEntity::getUpdateTime, start);
        if (end != null) {
            wrapper.lt(WorkOrderEntity::getUpdateTime, end);
        }
        return workOrderDao.selectCount(wrapper);
    }

    /**
     * 逐日新增/完成。
     * <p>
     * 没有数据的日子显式补 0：让前端自己补洞的话，"哪几天真的是 0"和
     * "后端忘了返回这一天"在界面上看起来一模一样。
     */
    private List<DailyCountRes> daily(LocalDate start, LocalDate end) {
        Map<String, Long> created = countDaily("create_time", start, false);
        Map<String, Long> completed = countDaily("update_time", start, true);

        List<DailyCountRes> result = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            String key = day.toString();
            result.add(new DailyCountRes(key,
                    created.getOrDefault(key, 0L),
                    completed.getOrDefault(key, 0L)));
        }
        return result;
    }

    /**
     * 按天分组计数。
     *
     * @param timeColumn    用于分组的列（新增看 create_time，完成看 update_time）
     * @param completedOnly true 时只统计已解决/已关闭的单据
     */
    private Map<String, Long> countDaily(String timeColumn, LocalDate start, boolean completedOnly) {
        // TO_CHAR 而不是 CAST(... AS DATE): 返回值直接是 YYYY-MM-DD 字符串, 不依赖
        // JDBC 把 date 映射成 java.sql.Date 还是 LocalDate。列名是内部常量, 不来自入参。
        String dayExpression = "TO_CHAR(" + timeColumn + ", 'YYYY-MM-DD')";
        QueryWrapper<WorkOrderEntity> wrapper = Wrappers.<WorkOrderEntity>query()
                .select(dayExpression + " AS day", "COUNT(*) AS cnt")
                .ge(timeColumn, start.atStartOfDay())
                .groupBy(dayExpression)
                .orderByAsc(dayExpression);
        if (completedOnly) {
            wrapper.in("status", WorkOrderStatus.RESOLVED.name(), WorkOrderStatus.CLOSED.name());
        }

        List<Map<String, Object>> rows = workOrderDao.selectMaps(wrapper);
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object day = row.get("day");
            if (day != null) {
                counts.put(String.valueOf(day), toLong(row.get("cnt")));
            }
        }
        return counts;
    }

    /**
     * 最近更新的工单。
     * <p>
     * 用 {@code COALESCE(update_time, create_time)} 排序，而不是只按 update_time：
     * PostgreSQL 的 DESC 默认把 NULL 排在最前，历史数据的 update_time 一旦为空，
     * 这些行会一直霸占"最近"的位置。
     */
    private List<RecentWorkOrderRes> recent() {
        List<WorkOrderEntity> entities = workOrderDao.selectList(
                Wrappers.<WorkOrderEntity>query()
                        .orderByDesc("COALESCE(update_time, create_time)")
                        .orderByDesc("id")
                        .last("LIMIT " + RECENT_LIMIT));
        return workOrderConverter.toRecentResList(entities);
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
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
        entity.setImageUrls(normalizeImageUrls(command.getImageUrls()));
        entity.setAiConversationId(trimToNull(command.getAiConversationId()));
        entity.setAiSelectedOptionId(command.getAiSelectedOptionId());

        // 状态由服务端定, 入参里没有 status 字段 —— 见 WorkOrderStatus 的说明。
        // 自助解决记录复用 RESOLVED，不新增状态值；受理人就是建单人。
        boolean selfResolved = Boolean.TRUE.equals(command.getSelfResolved());
        entity.setSelfResolved(selfResolved);
        entity.setStatus(selfResolved ? WorkOrderStatus.RESOLVED : WorkOrderStatus.NEW);
        if (selfResolved) {
            // 审计字段的填充只在字段为空时生效，所以这里显式写入 createBy，
            // 同时把 assignee 一起定下，避免插入后再补一次 UPDATE。
            Long operatorId = AuditMetaObjectHandler.currentOperatorId();
            entity.setCreateBy(operatorId);
            entity.setAssigneeId(operatorId);
        }

        // ASSIGN_ID 在插入前就把雪花 id 填回 entity, 所以插入后能直接取
        workOrderDao.insert(entity);

        // 自助解决记录不需要通知超管：它已经是终态，也没有人需要去处理。
        // 普通工单的旁路事情(通知超管)由事件带出去 —— 这里不认识消息模块,
        // 消息发不出去也不会影响建单, 理由见 WorkOrderCreatedNotifier。
        if (!selfResolved) {
            eventPublisher.publishEvent(new WorkOrderCreatedEvent(
                    entity.getId(), entity.getTitle(), entity.getType(), entity.getCreateBy()));
        }

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
     * 解析列表范围。
     *
     * @return {@code null} 表示不按人过滤（all / 未传）；非法值直接按业务错误拒绝
     */
    private static ListScope parseScope(String raw) {
        if (!StringUtils.hasText(raw) || "all".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        String value = raw.trim();
        if ("created".equalsIgnoreCase(value)) {
            return ListScope.CREATED;
        }
        if ("assigned".equalsIgnoreCase(value)) {
            return ListScope.ASSIGNED;
        }
        R.error(WorkOrderRsm.WORK_ORDER_SCOPE_INVALID);
        return null;
    }

    /** 列表范围。语义对应"我提交的"和"我受理的" */
    private enum ListScope {
        CREATED,
        ASSIGNED
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

    /**
     * 校验图片地址并落成"一行一个"的文本。
     *
     * <p>
     * 只放行两种地址：本项目的上传路径（{@code /file/...}）和明确的 http(s) 链接。
     * 这些值最终会渲染成 {@code <img src>}，{@code javascript:} / {@code data:} 之类
     * 没有正当用途，直接拒绝。重复的链接只留一份，用户重复上传同一张图不会让工单里
     * 出现两条一模一样的记录。
     */
    private static String normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : imageUrls) {
            if (!StringUtils.hasText(raw)) {
                return R.error(WorkOrderRsm.WORK_ORDER_IMAGE_URL_INVALID);
            }
            String url = raw.trim();
            // 换行是存储格式的分隔符，地址自己带换行会把一条链接拆成两条
            if (url.length() > MAX_IMAGE_URL_LENGTH
                    || url.contains("\n")
                    || url.contains("\r")
                    || !isAllowedImageUrl(url)) {
                return R.error(WorkOrderRsm.WORK_ORDER_IMAGE_URL_INVALID);
            }
            normalized.add(url);
        }
        return String.join("\n", normalized);
    }

    private static boolean isAllowedImageUrl(String url) {
        if (url.startsWith("/file/")) {
            // 相对地址会被拼到网关后面请求，不允许跳出 /file/ 目录
            return !url.contains("..");
        }
        return url.startsWith("https://") || url.startsWith("http://");
    }
}
