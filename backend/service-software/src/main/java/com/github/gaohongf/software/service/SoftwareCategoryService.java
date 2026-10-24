package com.github.gaohongf.software.service;

import java.util.List;

import com.github.gaohongf.software.entity.req.SaveSoftwareCategoryCommand;
import com.github.gaohongf.software.entity.res.SoftwareCategoryRes;

/** 软件分类 */
public interface SoftwareCategoryService {

    List<SoftwareCategoryRes> list();

    Long create(SaveSoftwareCategoryCommand command);

    void update(Long id, SaveSoftwareCategoryCommand command);

    void delete(Long id);
}
