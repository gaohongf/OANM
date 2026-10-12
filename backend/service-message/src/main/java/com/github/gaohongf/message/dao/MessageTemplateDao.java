package com.github.gaohongf.message.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.message.entity.po.MessageTemplateEntity;

/**
 * 消息模板 DAO。查询走条件构造器, 见 {@link NotificationDao} 的说明。
 * <p>
 * 查询条件的形态（{@code template_key} + {@code channel} 两个等值）由
 * {@code MessageTemplateCache} 拼装 —— 那里同时是唯一的调用点。
 */
@Mapper
public interface MessageTemplateDao extends BaseMapper<MessageTemplateEntity> {
}
