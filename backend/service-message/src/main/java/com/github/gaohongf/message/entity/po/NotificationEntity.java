package com.github.gaohongf.message.entity.po;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.message.mq.MessageHandlerType;
import com.github.gaohongf.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内信, 对应 {@code notifications} 表。
 *
 * <h2>为什么 {@code channel} 直接是枚举</h2>
 * MyBatis 默认按 {@code name()} 存取枚举（没有 {@code @EnumValue}/{@code IEnum} 时走
 * {@code EnumTypeHandler}）, 所以列里存的是 {@code APP} / {@code EMAIL}, 与
 * {@code MessageHandlerType} 逐字一致。加渠道时改一个枚举, 库里的历史值仍然有效 ——
 * 这正是用同一个枚举而不是各存各的字符串的理由。
 *
 * <h2>审计字段</h2>
 * 都来自 {@link BaseEntity}, 由 {@code AuditMetaObjectHandler} 填充。注意 {@code createBy}
 * 在消费线程上是<b>空</b>的: 那里没有 sa-token 上下文, 取不到"当前登录用户"
 * （见那个处理器的说明）。这不是缺陷 —— 消息的触发人在 {@code sender} 列里,
 * 而 {@code createBy} 表达的是"谁写的这一行", 对 MQ 消费者而言没有这个主体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notifications")
public class NotificationEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 幂等键, 唯一索引 {@code uk_notifications_event_id}。 */
    private String eventId;

    private Long receiver;

    /** 触发人 id, 系统消息为 null。 */
    private Long sender;

    /** 渠道, 见类注释。 */
    private MessageHandlerType channel;

    /** 模板键。 */
    private String type;

    /** 渲染快照, 不是模板引用 —— 见 schema.sql 里的说明。 */
    private String title;

    private String content;

    /** 业务 id(如工单 id), 前端点击后跳转。 */
    private String bizId;

    /** 0-未读 1-已读。用 {@code Integer} 而不是 boolean: 库里的列就是 int, 前端也按 0/1 判断。 */
    private Integer readFlag;

    private LocalDateTime readTime;
}
