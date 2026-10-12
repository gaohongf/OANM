package com.github.gaohongf.message.entity.req;

import java.util.Locale;

/**
 * 收件箱列表的已读筛选。
 *
 * <h2>为什么是个枚举而不是布尔</h2>
 * "只看未读"和"只看已读"都是真实需求, 而 {@code unread=true/false} 这种写法里
 * "不传"和"传 false"的区别全靠调用方自觉 —— 少传一个参数就从"全部"变成"只看已读",
 * 而两者在界面上是两屏完全不同的内容。三值枚举让"全部"必须显式写出来。
 *
 * <h2>为什么解析失败退回 {@link #ALL}</h2>
 * 这是<b>查询条件</b>, 不是写操作: 取值不认识时给最常见的那个视图（全部）比报 400 更合适 ——
 * 报错只会让用户面对一个打不开的页面, 而他并没有做错什么（真正的原因多半是前端版本旧）。
 * 反过来说, 这个取舍不能出现在写路径上: 那里解析失败必须拒绝, 否则会把"未知状态"
 * 当成某个具体状态去改数据。
 */
public enum NotificationReadState {

    /** 全部 */
    ALL,

    /** 只看未读 */
    UNREAD,

    /** 只看已读 */
    READ;

    /** 把查询参数解析成枚举, 空/不认识的值都按 {@link #ALL} 处理（见类注释）。 */
    public static NotificationReadState parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }
}
