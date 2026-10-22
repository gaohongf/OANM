package com.github.gaohongf.wo.entity.res;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 概览里的"最近工单动态"。
 *
 * <p>
 * 只带状态与标题，<b>不带 {@code @User} 字段</b>：列表页的解析是每个用户字段一次
 * 远程调用，这里一次要返回 6 条，为了一个头像去放大厅接口的延迟不划算。
 * 所以文案写成"某张工单变成了什么状态"，而不是"某个人做了什么"。
 */
@Data
public class RecentWorkOrderRes {

    /** 雪花 id 的字符串形式，理由同 {@link WorkOrderRes} */
    private String id;

    private String title;

    /** 工单类型: DEMAND / FAULT */
    private String type;

    /** 状态: NEW / ACCEPTED / RESOLVED / CLOSED */
    private String status;

    /** 最近一次变更时间；"多久以前"由前端按这个字段算 */
    private LocalDateTime updateTime;
}
