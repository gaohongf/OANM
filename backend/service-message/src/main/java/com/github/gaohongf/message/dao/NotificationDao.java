package com.github.gaohongf.message.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.message.entity.po.NotificationEntity;

/**
 * 站内信 DAO。
 *
 * <h2>为什么没有一条 XML</h2>
 * 收件箱的三个操作都能用条件构造器表达, 而且<b>都带着 {@code receiver} 条件</b>:
 * <ul>
 *   <li>列表: receiver [+ read_flag] + id DESC + 分页</li>
 *   <li>未读数: receiver + read_flag = 0 的 count</li>
 *   <li>已读: update ... where id = ? <b>and receiver = ?</b></li>
 * </ul>
 * 最后一条的 {@code receiver} 是<b>安全条件而不是过滤条件</b>: 只按 id 更新的话,
 * 任何人把 URL 里的 id 换成别人的就能标记别人的消息（越权）。这条约束写在
 * {@code NotificationServiceImpl} 里并配了注释 —— 它太容易被"顺手简化"掉了。
 */
@Mapper
public interface NotificationDao extends BaseMapper<NotificationEntity> {
}
