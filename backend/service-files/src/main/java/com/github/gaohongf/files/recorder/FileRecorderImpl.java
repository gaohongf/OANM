package com.github.gaohongf.files.recorder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.recorder.FileRecorder;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.gaohongf.files.dao.FileDetailDao;
import com.github.gaohongf.files.entity.po.FileDetailEntity;

/**
 * X File Storage 的上传记录实现: 记录落在 PostgreSQL 的 {@code file_detail} 表。
 *
 * <h2>为什么按 url 定位记录</h2>
 * 那个库对外的所有操作（下载、删除、判断是否存在）都以 url 为入口,
 * {@link #getByUrl(String)} 与 {@link #delete(String)} 的契约本身就是 url。
 * {@link #update(FileInfo)} 也按 url 更新, 与这两个方法保持同一把钥匙。
 *
 * <p>
 * 同一个 url 理论上只会有一条记录, 但表上没有唯一约束, 所以查询取第一条而不是
 * 用 {@code selectOne} —— 后者在脏数据下会直接抛异常, 让一次普通下载失败。
 */
@Service
public class FileRecorderImpl implements FileRecorder {

    private final FileDetailDao fileDetailDao;

    public FileRecorderImpl(FileDetailDao fileDetailDao) {
        this.fileDetailDao = fileDetailDao;
    }

    @Override
    public boolean save(FileInfo fileInfo) {
        FileDetailEntity entity = toEntity(Objects.requireNonNull(fileInfo, "fileInfo"));
        boolean saved = fileDetailDao.insert(entity) > 0;
        if (saved) {
            // 回写 id: 库在一次上传的后续步骤里会拿它来更新这条记录
            fileInfo.setId(entity.getId());
        }
        return saved;
    }

    @Override
    public void update(FileInfo fileInfo) {
        Objects.requireNonNull(fileInfo, "fileInfo");
        boolean byId = StringUtils.hasText(fileInfo.getId());
        boolean byUrl = StringUtils.hasText(fileInfo.getUrl());
        if (!byId && !byUrl) {
            return;
        }
        LambdaUpdateWrapper<FileDetailEntity> wrapper = Wrappers.lambdaUpdate();
        if (byId) {
            wrapper.eq(FileDetailEntity::getId, fileInfo.getId());
        }
        if (byUrl) {
            wrapper.eq(FileDetailEntity::getUrl, fileInfo.getUrl());
        }
        fileDetailDao.update(toEntity(fileInfo), wrapper);
    }

    @Override
    public FileInfo getByUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        List<FileDetailEntity> found = fileDetailDao.selectList(
                Wrappers.<FileDetailEntity>lambdaQuery()
                        .eq(FileDetailEntity::getUrl, url)
                        // 表上没有 url 唯一约束, 脏数据下 selectOne 会抛异常; 取第一条即可
                        .last("LIMIT 1"));
        return found.isEmpty() ? null : toFileInfo(found.get(0));
    }

    @Override
    public boolean delete(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        return fileDetailDao.delete(Wrappers.<FileDetailEntity>lambdaQuery()
                .eq(FileDetailEntity::getUrl, url)) > 0;
    }

    /**
     * 保存分片记录。
     *
     * <p>
     * 只有手动分片上传才会走到这里, 本服务没有 {@code file_part_detail} 表, 也没有开启
     * 分片上传。这里直接抛异常而不是空实现: 将来有人接入分片上传却忘了补这张表时,
     * 问题会在第一次上传就暴露, 而不是变成"分片记录悄悄丢了"。
     */
    @Override
    public void saveFilePart(FilePartInfo filePartInfo) {
        throw new UnsupportedOperationException("本服务未启用分片上传, 请先补 file_part_detail 表");
    }

    /**
     * 删除某个上传任务的全部分片记录。
     *
     * <p>
     * 与 {@link #saveFilePart(FilePartInfo)} 一样, 只在分片上传的完成/取消路径上调用。
     */
    @Override
    public void deleteFilePartByUploadId(String uploadId) {
        throw new UnsupportedOperationException("本服务未启用分片上传, 请先补 file_part_detail 表");
    }

    /** 库里是 timestamptz, 实体用 LocalDateTime 承接; 两边都按 JVM 默认时区换算 */
    private static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static Date toDate(LocalDateTime dateTime) {
        return dateTime == null ? null : Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static FileDetailEntity toEntity(FileInfo fileInfo) {
        FileDetailEntity entity = new FileDetailEntity();
        // 为空时由 @TableId(ASSIGN_UUID) 生成; 已有 id 时沿用, 更新步骤才能按 id 定位
        entity.setId(fileInfo.getId());
        entity.setCreateTime(toLocalDateTime(fileInfo.getCreateTime()));
        entity.setUrl(fileInfo.getUrl());
        entity.setSize(fileInfo.getSize());
        entity.setFilename(fileInfo.getFilename());
        entity.setOriginalFilename(fileInfo.getOriginalFilename());
        entity.setBasePath(fileInfo.getBasePath());
        entity.setPath(fileInfo.getPath());
        entity.setExt(fileInfo.getExt());
        entity.setContentType(fileInfo.getContentType());
        entity.setPlatform(fileInfo.getPlatform());
        entity.setThUrl(fileInfo.getThUrl());
        entity.setThFilename(fileInfo.getThFilename());
        entity.setThSize(fileInfo.getThSize());
        entity.setThContentType(fileInfo.getThContentType());
        entity.setObjectId(fileInfo.getObjectId());
        entity.setObjectType(fileInfo.getObjectType());
        entity.setAttr(fileInfo.getAttr());
        return entity;
    }

    private static FileInfo toFileInfo(FileDetailEntity entity) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(entity.getId());
        fileInfo.setCreateTime(toDate(entity.getCreateTime()));
        fileInfo.setUrl(entity.getUrl());
        fileInfo.setSize(entity.getSize());
        fileInfo.setFilename(entity.getFilename());
        fileInfo.setOriginalFilename(entity.getOriginalFilename());
        fileInfo.setBasePath(entity.getBasePath());
        fileInfo.setPath(entity.getPath());
        fileInfo.setExt(entity.getExt());
        fileInfo.setContentType(entity.getContentType());
        fileInfo.setPlatform(entity.getPlatform());
        fileInfo.setThUrl(entity.getThUrl());
        fileInfo.setThFilename(entity.getThFilename());
        fileInfo.setThSize(entity.getThSize());
        fileInfo.setThContentType(entity.getThContentType());
        fileInfo.setObjectId(entity.getObjectId());
        fileInfo.setObjectType(entity.getObjectType());
        fileInfo.setAttr(entity.getAttr());
        return fileInfo;
    }
}
