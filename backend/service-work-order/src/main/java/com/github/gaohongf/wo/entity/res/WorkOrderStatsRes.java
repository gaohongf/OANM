package com.github.gaohongf.wo.entity.res;

import java.util.List;

import lombok.Data;

/**
 * 概览统计。
 *
 * <h2>为什么单独一个接口</h2>
 * 概览要的是"各状态数量 + 按天趋势 + 最近几条"，而列表接口只有分页和等值筛选。
 * 让前端拉 200 条回来自己数，既受分页上限影响，也会随着数据增长越来越不准；
 * 聚合本来就该在数据库里做。
 *
 * <h2>"已完成"的口径</h2>
 * 目前没有单独的"完成时间"字段，{@link #todayCompleted} 和
 * {@link DailyCountRes#getCompleted()} 用的是<b>状态为已解决/已关闭的单据的
 * {@code update_time}</b>。结单写入 {@code RESOLVED}，取消写入 {@code CLOSED}，
 * 但 {@code update_time} 也可能被后续操作刷新；之后再补
 * {@code resolved_time} 才能做到精确。这里不把它伪装成精确值。
 */
@Data
public class WorkOrderStatsRes {

    /** 未删除工单总数 */
    private long total;

    /** 待受理（NEW） */
    private long pending;

    /** 已受理（ACCEPTED） */
    private long accepted;

    /** 已解决（RESOLVED） */
    private long resolved;

    /** 已关闭（CLOSED） */
    private long closed;

    /** 本周（周一起）新建 */
    private long weekCreated;

    /** 上周新建，用于"较上周"的对比 */
    private long lastWeekCreated;

    /** 今日完成，口径见类注释 */
    private long todayCompleted;

    /** 昨日完成，用于"较昨日"的对比 */
    private long yesterdayCompleted;

    /** 近 N 天逐日统计，按日期升序；没有数据的日子补 0，前端不用自己补洞 */
    private List<DailyCountRes> daily;

    /** 最近更新的工单，按更新时间倒序，最多 6 条 */
    private List<RecentWorkOrderRes> recent;
}
