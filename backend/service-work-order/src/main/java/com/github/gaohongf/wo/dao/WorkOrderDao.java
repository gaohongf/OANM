package com.github.gaohongf.wo.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.wo.entity.po.WorkOrderEntity;

/**
 * 工单 DAO。
 * <p>
 * 目前所有查询都能用条件构造器表达（状态等值 + 标题/描述模糊 + 分页 + 排序），
 * 所以没有 XML —— 和 {@code MenuDao} 一样走纯 {@link BaseMapper}。
 * 等到需要 JOIN（比如把受理人一次性带出来）或聚合时再加 XML。
 */
@Mapper
public interface WorkOrderDao extends BaseMapper<WorkOrderEntity> {
}
