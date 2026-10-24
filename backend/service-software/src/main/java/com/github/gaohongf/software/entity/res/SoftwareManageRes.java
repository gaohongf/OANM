package com.github.gaohongf.software.entity.res;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 管理端列表行：包含全部未删除版本（含停用），所以计数口径与下载页不同。
 */
@Data
public class SoftwareManageRes {

    private String id;

    private String name;

    private String vendor;

    private String categoryId;

    private String categoryName;

    /**
     * 简介。
     * <p>
     * 列表里也带上：编辑表单要回填它。不带的话，管理员改个名字提交空简介，
     * 就会把原来的简介静默清空。
     */
    private String description;

    /** 最新未删除版本号（无论是否启用） */
    private String latestVersionNo;

    /** 全部未删除版本覆盖的平台 */
    private List<String> platforms;

    /** 全部未删除版本数 */
    private int versionCount;

    /** 其中启用中的版本数 */
    private int enabledVersionCount;

    /** 全部未删除版本的下载次数之和 */
    private long downloadCount;

    private Boolean enabled;

    private LocalDateTime updateTime;
}
