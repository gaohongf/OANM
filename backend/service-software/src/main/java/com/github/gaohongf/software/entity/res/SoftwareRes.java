package com.github.gaohongf.software.entity.res;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 软件下载列表行。
 *
 * <p>
 * 只统计启用中的版本；`downloadCount` 例外，它是全部未删除版本之和 ——
 * 停用一个旧版本不该让历史下载量凭空消失。
 */
@Data
public class SoftwareRes {

    private String id;

    private String name;

    private String vendor;

    private String categoryId;

    private String categoryName;

    /** 最新启用版本的 id；列表页可以据此直接下载最新版 */
    private String latestVersionId;

    private String latestVersionNo;

    /** 启用版本覆盖的平台，按 WINDOWS→MACOS→LINUX→COMMON 排列 */
    private List<String> platforms;

    /** 启用版本数 */
    private int versionCount;

    /** 全部未删除版本的下载次数之和 */
    private long downloadCount;

    private LocalDateTime updateTime;
}
