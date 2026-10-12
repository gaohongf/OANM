package com.github.gaohongf.message.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.message.mq.MessageHandlerType;
import com.github.gaohongf.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板, 对应 {@code message_templates} 表。
 * <p>
 * 唯一键是 {@code (template_key, channel)}, 所以查模板必须两个条件都带上 ——
 * 只按 key 查会在同一件事同时有站内信和邮件模板时抛
 * {@code TooManyResultsException}（不是返回一条, 是直接失败）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_templates")
public class MessageTemplateEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String templateKey;

    /** 渠道, 与 {@code notifications.channel} 同类型。 */
    private MessageHandlerType channel;

    private String titleTemplate;

    private String contentTemplate;

    /** 0-停用 1-启用。停用的模板按"没找到"处理, 见 MessageTemplateCache。 */
    private Integer enabled;

    private String remark;
}
