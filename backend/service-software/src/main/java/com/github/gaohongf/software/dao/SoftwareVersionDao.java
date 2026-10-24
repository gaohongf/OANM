package com.github.gaohongf.software.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.software.entity.po.SoftwareVersionEntity;

/** 版本 DAO。下载计数的原子自增也走条件构造器 */
@Mapper
public interface SoftwareVersionDao extends BaseMapper<SoftwareVersionEntity> {
}
