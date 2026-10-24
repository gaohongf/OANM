package com.github.gaohongf.software.service;

import com.github.gaohongf.model.PageRes;
import com.github.gaohongf.software.entity.req.SaveSoftwareCommand;
import com.github.gaohongf.software.entity.req.SaveSoftwareVersionCommand;
import com.github.gaohongf.software.entity.res.DownloadRes;
import com.github.gaohongf.software.entity.res.SoftwareDetailRes;
import com.github.gaohongf.software.entity.res.SoftwareManageDetailRes;
import com.github.gaohongf.software.entity.res.SoftwareManageRes;
import com.github.gaohongf.software.entity.res.SoftwareRes;

/** 软件目录与版本 */
public interface SoftwareService {

    /**
     * 下载页列表：只包含上架的软件。
     * <p>
     * 还没有启用版本的软件也会出现（最新版本与下载按钮为空），
     * 这样管理员刚建好目录就能在下载页看到它，而不是"建了却消失"。
     */
    PageRes<SoftwareRes> pagePublic(long current, long size, String keyword,
                                    Long categoryId, String platform);

    /** 下载页详情：只包含启用中的版本 */
    SoftwareDetailRes findPublicDetail(Long id);

    /** 下载计数，并返回本次下载地址 */
    DownloadRes download(Long versionId);

    /** 管理端列表：包含停用软件与停用版本 */
    PageRes<SoftwareManageRes> pageManage(long current, long size, String keyword,
                                          Long categoryId, String platform, Boolean enabled);

    /** 管理端详情：包含全部未删除版本 */
    SoftwareManageDetailRes findManageDetail(Long id);

    Long create(SaveSoftwareCommand command);

    void update(Long id, SaveSoftwareCommand command);

    void setEnabled(Long id, boolean enabled);

    void delete(Long id);

    Long createVersion(Long softwareId, SaveSoftwareVersionCommand command);

    void updateVersion(Long versionId, SaveSoftwareVersionCommand command);

    void setVersionEnabled(Long versionId, boolean enabled);

    void deleteVersion(Long versionId);
}
