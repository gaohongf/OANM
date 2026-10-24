package com.github.gaohongf.software.service.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.software.dao.SoftwareCategoryDao;
import com.github.gaohongf.software.dao.SoftwareDao;
import com.github.gaohongf.software.entity.po.SoftwareCategoryEntity;
import com.github.gaohongf.software.entity.po.SoftwareEntity;
import com.github.gaohongf.software.entity.req.SaveSoftwareCategoryCommand;
import com.github.gaohongf.software.entity.res.SoftwareCategoryRes;
import com.github.gaohongf.software.mapstruct.SoftwareConverter;
import com.github.gaohongf.software.rsm.SoftwareRsm;
import com.github.gaohongf.software.service.SoftwareCategoryService;
import com.lingyun.base.rsm.R;

import lombok.AllArgsConstructor;

/**
 * 分类维护。
 *
 * <h2>为什么删除要先数软件</h2>
 * 没有外键（逻辑删除的表加外键也拦不住软删行），所以只能在应用层判断。
 * 这里刻意把已停用的软件也算进去：分类被隐藏了，但那些软件仍然指着它。
 */
@AllArgsConstructor
@Service
public class SoftwareCategoryServiceImpl implements SoftwareCategoryService {

    private final SoftwareCategoryDao categoryDao;
    private final SoftwareDao softwareDao;
    private final SoftwareConverter converter;

    @Override
    public List<SoftwareCategoryRes> list() {
        List<SoftwareCategoryEntity> entities = categoryDao.selectList(
                Wrappers.<SoftwareCategoryEntity>lambdaQuery()
                        .orderByAsc(SoftwareCategoryEntity::getSort)
                        .orderByAsc(SoftwareCategoryEntity::getId));
        return converter.toCategoryResList(entities);
    }

    @Override
    public Long create(SaveSoftwareCategoryCommand command) {
        String name = command.getName().trim();
        requireUniqueName(name, null);

        SoftwareCategoryEntity entity = new SoftwareCategoryEntity();
        entity.setName(name);
        entity.setSort(command.getSort());
        try {
            categoryDao.insert(entity);
        } catch (DuplicateKeyException duplicated) {
            // 预检查只能挡住顺序提交；两个管理员同时提交时靠部分唯一索引兜底
            R.error(SoftwareRsm.SOFTWARE_CATEGORY_EXISTS);
        }
        return entity.getId();
    }

    @Override
    public void update(Long id, SaveSoftwareCategoryCommand command) {
        SoftwareCategoryEntity current = categoryDao.selectById(id);
        if (current == null) {
            R.error(SoftwareRsm.SOFTWARE_CATEGORY_NOT_FOUND);
        }

        String name = command.getName().trim();
        requireUniqueName(name, id);
        current.setName(name);
        current.setSort(command.getSort());

        int rows;
        try {
            rows = categoryDao.updateById(current);
        } catch (DuplicateKeyException duplicated) {
            R.error(SoftwareRsm.SOFTWARE_CATEGORY_EXISTS);
            return;
        }
        if (rows == 0) {
            R.error(SoftwareRsm.SOFTWARE_CONCURRENT_MODIFIED);
        }
    }

    @Override
    public void delete(Long id) {
        SoftwareCategoryEntity current = categoryDao.selectById(id);
        if (current == null) {
            R.error(SoftwareRsm.SOFTWARE_CATEGORY_NOT_FOUND);
        }

        // 逻辑删除的行已被 @TableLogic 排除，所以这里数到的都是"还在用"的软件
        Long used = softwareDao.selectCount(Wrappers.<SoftwareEntity>lambdaQuery()
                .eq(SoftwareEntity::getCategoryId, id));
        if (used != null && used > 0) {
            R.error(SoftwareRsm.SOFTWARE_CATEGORY_IN_USE);
        }
        categoryDao.deleteById(id);
    }

    private void requireUniqueName(String name, Long excludeId) {
        Long count = categoryDao.selectCount(Wrappers.<SoftwareCategoryEntity>lambdaQuery()
                .eq(SoftwareCategoryEntity::getName, name)
                .ne(excludeId != null, SoftwareCategoryEntity::getId, excludeId));
        if (count != null && count > 0) {
            R.error(SoftwareRsm.SOFTWARE_CATEGORY_EXISTS);
        }
    }
}
