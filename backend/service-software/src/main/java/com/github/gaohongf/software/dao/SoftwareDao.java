package com.github.gaohongf.software.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.software.entity.po.SoftwareEntity;

/** 软件 DAO */
@Mapper
public interface SoftwareDao extends BaseMapper<SoftwareEntity> {
}
