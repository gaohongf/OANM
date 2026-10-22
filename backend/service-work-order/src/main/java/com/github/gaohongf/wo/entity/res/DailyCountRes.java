package com.github.gaohongf.wo.entity.res;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 某一天的工单新增/完成数。
 *
 * <p>
 * {@code date} 是 {@code YYYY-MM-DD} 字符串而不是 {@code LocalDate}：前端只拿它做
 * 横轴标签和表格行，不需要时区语义；用字符串还能避开"日期被序列化成数组"这种
 * Jackson 配置差异。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyCountRes {

    /** 日期，格式 YYYY-MM-DD */
    private String date;

    /** 当天的新增数 */
    private long created;

    /** 当天的完成数，口径见 {@link WorkOrderStatsRes} */
    private long completed;
}
