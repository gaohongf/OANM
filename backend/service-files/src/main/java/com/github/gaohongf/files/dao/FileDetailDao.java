package com.github.gaohongf.files.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.gaohongf.files.entity.po.FileDetailEntity;

/**
 * 文件上传记录 DAO。
 *
 * <p>
 * 全部操作都是按 {@code url} 的等值查询, 用条件构造器足够, 不需要 XML。
 */
@Mapper
public interface FileDetailDao extends BaseMapper<FileDetailEntity> {
}
