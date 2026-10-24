package com.github.gaohongf.software.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.software.dao.SoftwareCategoryDao;
import com.github.gaohongf.software.dao.SoftwareDao;
import com.github.gaohongf.software.dao.SoftwareVersionDao;
import com.github.gaohongf.software.entity.po.SoftwareCategoryEntity;
import com.github.gaohongf.software.entity.po.SoftwareEntity;
import com.github.gaohongf.software.entity.po.SoftwarePlatform;
import com.github.gaohongf.software.entity.po.SoftwareVersionEntity;
import com.github.gaohongf.software.entity.req.SaveSoftwareCommand;
import com.github.gaohongf.software.entity.req.SaveSoftwareVersionCommand;
import com.github.gaohongf.software.entity.res.DownloadRes;
import com.github.gaohongf.software.entity.res.SoftwareDetailRes;
import com.github.gaohongf.software.entity.res.SoftwareManageDetailRes;
import com.github.gaohongf.software.entity.res.SoftwareManageRes;
import com.github.gaohongf.software.entity.res.SoftwareRes;
import com.github.gaohongf.software.mapstruct.SoftwareConverter;
import com.github.gaohongf.software.rsm.SoftwareRsm;
import com.github.gaohongf.software.service.SoftwareService;
import com.lingyun.base.rsm.R;

import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;

/**
 * 软件目录、版本与下载。
 *
 * <h2>列表不做 N+1</h2>
 * 先分页查出软件，再用本页 id 一次性把版本和分类查回来，在内存里分组组装。
 * 每页最多 200 行，这个量级下比窗口函数 SQL 更直观。
 *
 * <h2>下载计数必须绕开实体更新</h2>
 * 计数是 {@code download_count = download_count + 1} 的原子自增。如果先查实体再
 * {@code updateById}，会把读到的旧计数写回去，正好丢掉并发的那几次下载；
 * 而且会把乐观锁的 version 一起带进更新。见 {@link #download(Long)}。
 */
@AllArgsConstructor
@Service
public class SoftwareServiceImpl implements SoftwareService {

    /**
     * 平台筛选用子查询而不是 JOIN：一个软件可能有多个版本的平台，JOIN 会放大分页结果。
     * 注意子查询里的 deleted = 0 必须自己写 —— MyBatis-Plus 只给主查询注入逻辑删除。
     * 平台值来自 {@link SoftwarePlatform} 枚举，不是用户输入，拼接是安全的。
     */
    private static final String PLATFORM_IN_SQL_TEMPLATE =
            "SELECT software_id FROM software_versions WHERE deleted = 0%s"
                    + " AND platform IN (%s)";

    private final SoftwareDao softwareDao;
    private final SoftwareVersionDao versionDao;
    private final SoftwareCategoryDao categoryDao;
    private final SoftwareConverter converter;

    @Override
    public PageRes<SoftwareRes> pagePublic(long current, long size, String keyword,
                                           Long categoryId, String platform) {
        long safeCurrent = PageRes.clampCurrent(current);
        long safeSize = PageRes.clampSize(size);
        SoftwarePlatform platformFilter = parsePlatform(platform);
        String search = normalizeKeyword(keyword);

        LambdaQueryWrapper<SoftwareEntity> wrapper = Wrappers.<SoftwareEntity>lambdaQuery()
                .eq(SoftwareEntity::getEnabled, true)
                .eq(categoryId != null, SoftwareEntity::getCategoryId, categoryId)
                .and(search != null, w -> w
                        .like(SoftwareEntity::getName, search)
                        .or()
                        .like(SoftwareEntity::getVendor, search));
        if (platformFilter != null) {
            // 下载页只认"有启用版本"的软件
            wrapper.inSql(SoftwareEntity::getId, platformInSql(platformFilter, true));
        }
        wrapper.orderByDesc(SoftwareEntity::getUpdateTime)
                .orderByDesc(SoftwareEntity::getId);

        IPage<SoftwareEntity> page = softwareDao.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(), List.of());
        }

        Map<Long, List<SoftwareVersionEntity>> versionsBySoftware = loadVersions(page.getRecords());
        Map<Long, String> categoryNames = loadCategoryNames(page.getRecords());

        List<SoftwareRes> records = page.getRecords().stream().map(entity -> {
            SoftwareRes res = converter.toRes(entity);
            res.setCategoryName(categoryNames.get(entity.getCategoryId()));
            fillPublicSummary(res, versionsBySoftware.getOrDefault(entity.getId(), List.of()));
            return res;
        }).toList();

        return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    public SoftwareDetailRes findPublicDetail(Long id) {
        SoftwareEntity entity = softwareDao.selectById(id);
        if (entity == null || !Boolean.TRUE.equals(entity.getEnabled())) {
            R.error(SoftwareRsm.SOFTWARE_NOT_FOUND);
        }

        List<SoftwareVersionEntity> all = loadVersionsOf(id);
        SoftwareDetailRes res = converter.toDetailRes(entity);
        res.setDescription(entity.getDescription());
        res.setCategoryName(findCategoryName(entity.getCategoryId()));
        fillPublicSummary(res, all);

        List<SoftwareVersionEntity> enabled = all.stream()
                .filter(version -> Boolean.TRUE.equals(version.getEnabled()))
                .toList();
        res.setVersions(converter.toVersionResList(enabled));
        return res;
    }

    @Override
    public DownloadRes download(Long versionId) {
        SoftwareVersionEntity version = versionDao.selectById(versionId);
        if (version == null) {
            R.error(SoftwareRsm.SOFTWARE_VERSION_NOT_FOUND);
        }

        SoftwareEntity software = softwareDao.selectById(version.getSoftwareId());
        if (software == null || !Boolean.TRUE.equals(software.getEnabled())) {
            R.error(SoftwareRsm.SOFTWARE_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(version.getEnabled())) {
            R.error(SoftwareRsm.SOFTWARE_VERSION_DISABLED);
        }
        if (!StringUtils.hasText(version.getFileUrl())
                || !version.getFileUrl().startsWith("/file/")
                || !StringUtils.hasText(version.getFileName())) {
            R.error(SoftwareRsm.SOFTWARE_FILE_INVALID);
        }

        // 条件里再带一次 enabled：读完之后管理员刚好停用/删除时，不能让计数照样加。
        // update(null, wrapper) 不经过乐观锁，正是计数需要的语义。
        int rows = versionDao.update(null, Wrappers.<SoftwareVersionEntity>lambdaUpdate()
                .eq(SoftwareVersionEntity::getId, versionId)
                .eq(SoftwareVersionEntity::getEnabled, true)
                .setSql("download_count = download_count + 1"));
        if (rows == 0) {
            R.error(SoftwareRsm.SOFTWARE_DOWNLOAD_UNAVAILABLE);
        }
        return new DownloadRes(version.getFileUrl(), version.getFileName());
    }

    @Override
    public PageRes<SoftwareManageRes> pageManage(long current, long size, String keyword,
                                                 Long categoryId, String platform, Boolean enabled) {
        long safeCurrent = PageRes.clampCurrent(current);
        long safeSize = PageRes.clampSize(size);
        SoftwarePlatform platformFilter = parsePlatform(platform);
        String search = normalizeKeyword(keyword);

        LambdaQueryWrapper<SoftwareEntity> wrapper = Wrappers.<SoftwareEntity>lambdaQuery()
                .eq(enabled != null, SoftwareEntity::getEnabled, enabled)
                .eq(categoryId != null, SoftwareEntity::getCategoryId, categoryId)
                .and(search != null, w -> w
                        .like(SoftwareEntity::getName, search)
                        .or()
                        .like(SoftwareEntity::getVendor, search));
        if (platformFilter != null) {
            // 管理端把停用版本也算进来，否则筛出来的行和表格里的平台标签对不上
            wrapper.inSql(SoftwareEntity::getId, platformInSql(platformFilter, false));
        }
        wrapper.orderByDesc(SoftwareEntity::getUpdateTime)
                .orderByDesc(SoftwareEntity::getId);

        IPage<SoftwareEntity> page = softwareDao.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(), List.of());
        }

        Map<Long, List<SoftwareVersionEntity>> versionsBySoftware = loadVersions(page.getRecords());
        Map<Long, String> categoryNames = loadCategoryNames(page.getRecords());

        List<SoftwareManageRes> records = page.getRecords().stream().map(entity -> {
            SoftwareManageRes res = converter.toManageRes(entity);
            res.setCategoryName(categoryNames.get(entity.getCategoryId()));
            fillManageSummary(res, versionsBySoftware.getOrDefault(entity.getId(), List.of()));
            return res;
        }).toList();

        return new PageRes<>(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    public SoftwareManageDetailRes findManageDetail(Long id) {
        SoftwareEntity entity = softwareDao.selectById(id);
        if (entity == null) {
            R.error(SoftwareRsm.SOFTWARE_NOT_FOUND);
        }

        List<SoftwareVersionEntity> versions = loadVersionsOf(id);
        SoftwareManageDetailRes res = converter.toManageDetailRes(entity);
        res.setDescription(entity.getDescription());
        res.setCategoryName(findCategoryName(entity.getCategoryId()));
        fillManageSummary(res, versions);
        res.setVersions(converter.toManageVersionResList(versions));
        return res;
    }

    @Override
    public Long create(SaveSoftwareCommand command) {
        String name = command.getName().trim();
        requireCategory(command.getCategoryId());
        requireUniqueSoftwareName(command.getCategoryId(), name, null);

        SoftwareEntity entity = new SoftwareEntity();
        entity.setName(name);
        entity.setVendor(trimToNull(command.getVendor()));
        entity.setCategoryId(command.getCategoryId());
        entity.setDescription(trimToNull(command.getDescription()));
        entity.setEnabled(command.getEnabled() == null || command.getEnabled());
        try {
            softwareDao.insert(entity);
        } catch (DuplicateKeyException duplicated) {
            R.error(SoftwareRsm.SOFTWARE_NAME_EXISTS);
        }
        return entity.getId();
    }

    @Override
    public void update(Long id, SaveSoftwareCommand command) {
        SoftwareEntity current = softwareDao.selectById(id);
        if (current == null) {
            R.error(SoftwareRsm.SOFTWARE_NOT_FOUND);
        }

        String name = command.getName().trim();
        requireCategory(command.getCategoryId());
        requireUniqueSoftwareName(command.getCategoryId(), name, id);

        current.setName(name);
        current.setVendor(trimToNull(command.getVendor()));
        current.setCategoryId(command.getCategoryId());
        current.setDescription(trimToNull(command.getDescription()));
        if (command.getEnabled() != null) {
            current.setEnabled(command.getEnabled());
        }

        int rows;
        try {
            rows = softwareDao.updateById(current);
        } catch (DuplicateKeyException duplicated) {
            R.error(SoftwareRsm.SOFTWARE_NAME_EXISTS);
            return;
        }
        if (rows == 0) {
            R.error(SoftwareRsm.SOFTWARE_CONCURRENT_MODIFIED);
        }
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        SoftwareEntity current = softwareDao.selectById(id);
        if (current == null) {
            R.error(SoftwareRsm.SOFTWARE_NOT_FOUND);
        }
        current.setEnabled(enabled);
        int rows = softwareDao.updateById(current);
        if (rows == 0) {
            R.error(SoftwareRsm.SOFTWARE_CONCURRENT_MODIFIED);
        }
    }

    /**
     * 删除软件时连同版本一起逻辑删除。
     * <p>
     * 不做物理删除：物理文件还留在存储上，删库只会让它们更难追溯。文件清理是另一个问题。
     */
    @Override
    @Transactional
    public void delete(Long id) {
        SoftwareEntity current = softwareDao.selectById(id);
        if (current == null) {
            R.error(SoftwareRsm.SOFTWARE_NOT_FOUND);
        }

        softwareDao.deleteById(id);
        versionDao.delete(Wrappers.<SoftwareVersionEntity>lambdaQuery()
                .eq(SoftwareVersionEntity::getSoftwareId, id));
    }

    @Override
    public Long createVersion(Long softwareId, SaveSoftwareVersionCommand command) {
        SoftwareEntity software = softwareDao.selectById(softwareId);
        if (software == null) {
            R.error(SoftwareRsm.SOFTWARE_NOT_FOUND);
        }

        SoftwarePlatform platform = command.toPlatform();
        command.requireFileOnCreate();
        String versionNo = command.getVersionNo().trim();
        requireUniqueVersion(softwareId, versionNo, platform, null);

        SoftwareVersionEntity entity = new SoftwareVersionEntity();
        entity.setSoftwareId(softwareId);
        entity.setVersionNo(versionNo);
        entity.setPlatform(platform);
        entity.setReleaseNote(trimToNull(command.getReleaseNote()));
        entity.setFileId(command.getFileId());
        entity.setFileUrl(command.getFileUrl());
        entity.setFileName(command.getFileName());
        entity.setFileSize(command.getFileSize());
        entity.setDownloadCount(0L);
        entity.setEnabled(command.getEnabled() == null || command.getEnabled());
        try {
            versionDao.insert(entity);
        } catch (DuplicateKeyException duplicated) {
            R.error(SoftwareRsm.SOFTWARE_VERSION_DUPLICATED);
        }
        return entity.getId();
    }

    /**
     * 编辑版本。
     * <p>
     * 用条件更新而不是 {@code updateById}：后者会把读到的 {@code download_count} 一起写回，
     * 与并发下载互相覆盖。这里只 set 管理员真正能改的列，计数列完全不出现。
     */
    @Override
    public void updateVersion(Long versionId, SaveSoftwareVersionCommand command) {
        SoftwareVersionEntity current = versionDao.selectById(versionId);
        if (current == null) {
            R.error(SoftwareRsm.SOFTWARE_VERSION_NOT_FOUND);
        }

        SoftwarePlatform platform = command.toPlatform();
        String versionNo = command.getVersionNo().trim();
        requireUniqueVersion(current.getSoftwareId(), versionNo, platform, versionId);
        boolean replaceFile = command.shouldReplaceFile();
        boolean enabled = command.getEnabled() == null
                ? Boolean.TRUE.equals(current.getEnabled())
                : command.getEnabled();
        int currentVersion = current.getVersion() == null ? 0 : current.getVersion();

        LambdaUpdateWrapper<SoftwareVersionEntity> update = Wrappers.<SoftwareVersionEntity>lambdaUpdate()
                .eq(SoftwareVersionEntity::getId, versionId)
                .eq(SoftwareVersionEntity::getVersion, currentVersion)
                .set(SoftwareVersionEntity::getVersionNo, versionNo)
                .set(SoftwareVersionEntity::getPlatform, platform.name())
                .set(SoftwareVersionEntity::getReleaseNote, trimToNull(command.getReleaseNote()))
                .set(SoftwareVersionEntity::getEnabled, enabled)
                .set(SoftwareVersionEntity::getUpdateBy, StpUtil.getLoginIdAsLong())
                .set(SoftwareVersionEntity::getUpdateTime, LocalDateTime.now())
                .set(SoftwareVersionEntity::getVersion, currentVersion + 1);

        if (replaceFile) {
            update.set(SoftwareVersionEntity::getFileId, command.getFileId())
                    .set(SoftwareVersionEntity::getFileUrl, command.getFileUrl())
                    .set(SoftwareVersionEntity::getFileName, command.getFileName())
                    .set(SoftwareVersionEntity::getFileSize, command.getFileSize());
        }

        int rows;
        try {
            rows = versionDao.update(null, update);
        } catch (DuplicateKeyException duplicated) {
            R.error(SoftwareRsm.SOFTWARE_VERSION_DUPLICATED);
            return;
        }
        if (rows == 0) {
            R.error(SoftwareRsm.SOFTWARE_CONCURRENT_MODIFIED);
        }
    }

    @Override
    public void setVersionEnabled(Long versionId, boolean enabled) {
        SoftwareVersionEntity current = versionDao.selectById(versionId);
        if (current == null) {
            R.error(SoftwareRsm.SOFTWARE_VERSION_NOT_FOUND);
        }
        int currentVersion = current.getVersion() == null ? 0 : current.getVersion();

        int rows = versionDao.update(null, Wrappers.<SoftwareVersionEntity>lambdaUpdate()
                .eq(SoftwareVersionEntity::getId, versionId)
                .eq(SoftwareVersionEntity::getVersion, currentVersion)
                .set(SoftwareVersionEntity::getEnabled, enabled)
                .set(SoftwareVersionEntity::getUpdateBy, StpUtil.getLoginIdAsLong())
                .set(SoftwareVersionEntity::getUpdateTime, LocalDateTime.now())
                .set(SoftwareVersionEntity::getVersion, currentVersion + 1));
        if (rows == 0) {
            R.error(SoftwareRsm.SOFTWARE_CONCURRENT_MODIFIED);
        }
    }

    @Override
    public void deleteVersion(Long versionId) {
        SoftwareVersionEntity current = versionDao.selectById(versionId);
        if (current == null) {
            R.error(SoftwareRsm.SOFTWARE_VERSION_NOT_FOUND);
        }
        versionDao.deleteById(versionId);
    }

    // ------------------------------------------------------------------
    // 组装
    // ------------------------------------------------------------------

    /** 本页软件的全部未删除版本，按创建时间倒序（第一条即最新） */
    private Map<Long, List<SoftwareVersionEntity>> loadVersions(List<SoftwareEntity> records) {
        List<Long> ids = records.stream().map(SoftwareEntity::getId).toList();
        List<SoftwareVersionEntity> versions = versionDao.selectList(
                Wrappers.<SoftwareVersionEntity>lambdaQuery()
                        .in(SoftwareVersionEntity::getSoftwareId, ids)
                        .orderByDesc(SoftwareVersionEntity::getCreateTime)
                        .orderByDesc(SoftwareVersionEntity::getId));
        return versions.stream().collect(Collectors.groupingBy(SoftwareVersionEntity::getSoftwareId));
    }

    private List<SoftwareVersionEntity> loadVersionsOf(Long softwareId) {
        return versionDao.selectList(Wrappers.<SoftwareVersionEntity>lambdaQuery()
                .eq(SoftwareVersionEntity::getSoftwareId, softwareId)
                .orderByDesc(SoftwareVersionEntity::getCreateTime)
                .orderByDesc(SoftwareVersionEntity::getId));
    }

    private Map<Long, String> loadCategoryNames(List<SoftwareEntity> records) {
        Set<Long> ids = records.stream()
                .map(SoftwareEntity::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryDao.selectList(Wrappers.<SoftwareCategoryEntity>lambdaQuery()
                        .in(SoftwareCategoryEntity::getId, ids))
                .stream()
                .collect(Collectors.toMap(SoftwareCategoryEntity::getId, SoftwareCategoryEntity::getName));
    }

    private String findCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        SoftwareCategoryEntity category = categoryDao.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    /** 下载页口径：最新/平台/版本数只看启用版本，下载次数看全部未删除版本 */
    private static void fillPublicSummary(SoftwareRes res, List<SoftwareVersionEntity> all) {
        List<SoftwareVersionEntity> enabled = all.stream()
                .filter(version -> Boolean.TRUE.equals(version.getEnabled()))
                .toList();
        res.setVersionCount(enabled.size());
        res.setPlatforms(platformsOf(enabled));
        res.setDownloadCount(all.stream().mapToLong(SoftwareServiceImpl::downloadCountOf).sum());
        if (!enabled.isEmpty()) {
            SoftwareVersionEntity latest = enabled.get(0);
            res.setLatestVersionId(latest.getId() == null ? null : latest.getId().toString());
            res.setLatestVersionNo(latest.getVersionNo());
        }
    }

    /** 管理端口径：全部未删除版本（含停用） */
    private static void fillManageSummary(SoftwareManageRes res, List<SoftwareVersionEntity> all) {
        res.setVersionCount(all.size());
        res.setEnabledVersionCount((int) all.stream()
                .filter(version -> Boolean.TRUE.equals(version.getEnabled()))
                .count());
        res.setPlatforms(platformsOf(all));
        res.setDownloadCount(all.stream().mapToLong(SoftwareServiceImpl::downloadCountOf).sum());
        if (!all.isEmpty()) {
            res.setLatestVersionNo(all.get(0).getVersionNo());
        }
    }

    private static long downloadCountOf(SoftwareVersionEntity version) {
        return version.getDownloadCount() == null ? 0L : version.getDownloadCount();
    }

    private static List<String> platformsOf(List<SoftwareVersionEntity> versions) {
        Set<SoftwarePlatform> present = EnumSet.noneOf(SoftwarePlatform.class);
        for (SoftwareVersionEntity version : versions) {
            if (version.getPlatform() != null) {
                present.add(version.getPlatform());
            }
        }
        List<String> result = new ArrayList<>();
        for (SoftwarePlatform platform : SoftwarePlatform.values()) {
            if (present.contains(platform)) {
                result.add(platform.name());
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    private static SoftwarePlatform parsePlatform(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Optional<SoftwarePlatform> parsed = SoftwarePlatform.parse(raw);
        if (parsed.isEmpty()) {
            R.error(SoftwareRsm.SOFTWARE_PLATFORM_INVALID);
        }
        return parsed.get();
    }

    private static String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String platformInSql(SoftwarePlatform filter, boolean enabledOnly) {
        String platforms = filter == SoftwarePlatform.COMMON
                ? "'COMMON'"
                : "'" + filter.name() + "','COMMON'";
        String enabledCondition = enabledOnly ? " AND enabled = TRUE" : "";
        return String.format(PLATFORM_IN_SQL_TEMPLATE, enabledCondition, platforms);
    }

    private void requireCategory(Long categoryId) {
        if (categoryId == null || categoryDao.selectById(categoryId) == null) {
            R.error(SoftwareRsm.SOFTWARE_CATEGORY_NOT_FOUND);
        }
    }

    private void requireUniqueSoftwareName(Long categoryId, String name, Long excludeId) {
        Long count = softwareDao.selectCount(Wrappers.<SoftwareEntity>lambdaQuery()
                .eq(SoftwareEntity::getCategoryId, categoryId)
                .eq(SoftwareEntity::getName, name)
                .ne(excludeId != null, SoftwareEntity::getId, excludeId));
        if (count != null && count > 0) {
            R.error(SoftwareRsm.SOFTWARE_NAME_EXISTS);
        }
    }

    private void requireUniqueVersion(Long softwareId, String versionNo,
                                      SoftwarePlatform platform, Long excludeId) {
        Long count = versionDao.selectCount(Wrappers.<SoftwareVersionEntity>lambdaQuery()
                .eq(SoftwareVersionEntity::getSoftwareId, softwareId)
                .eq(SoftwareVersionEntity::getVersionNo, versionNo)
                .eq(SoftwareVersionEntity::getPlatform, platform.name())
                .ne(excludeId != null, SoftwareVersionEntity::getId, excludeId));
        if (count != null && count > 0) {
            R.error(SoftwareRsm.SOFTWARE_VERSION_DUPLICATED);
        }
    }
}
