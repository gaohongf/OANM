package com.github.gaohongf.software.rsm;

import org.springframework.http.HttpStatus;

import com.lingyun.base.rsm.RsmManager;
import com.lingyun.base.rsm.annotation.RsmInfo;

/**
 * 软件下载中心的消息键。
 *
 * <h2>不要给它加 @Component</h2>
 * 由 {@code SoftwareRsmConfiguration} 显式注册。没注册过的键解析不出模板,
 * 会退回默认消息 —— 拒绝被渲染成"成功", 而且不抛错、不告警。
 */
public class SoftwareRsm implements RsmManager {

    /** 按 id 查软件但不存在（已删除或从未创建），或该软件已停用 */
    @RsmInfo(template = "软件不存在", status = HttpStatus.NOT_FOUND)
    public static final String SOFTWARE_NOT_FOUND = "Software_SOFTWARE_NOT_FOUND";

    /** 新建/编辑软件时指定的分类不存在 */
    @RsmInfo(template = "软件分类不存在", status = HttpStatus.BAD_REQUEST)
    public static final String SOFTWARE_CATEGORY_NOT_FOUND = "Software_SOFTWARE_CATEGORY_NOT_FOUND";

    /** 分类名称重复 */
    @RsmInfo(template = "分类名称已存在", status = HttpStatus.CONFLICT)
    public static final String SOFTWARE_CATEGORY_EXISTS = "Software_SOFTWARE_CATEGORY_EXISTS";

    /** 分类下还有软件（含已停用的），不能删除 */
    @RsmInfo(template = "该分类下还有软件，不能删除", status = HttpStatus.CONFLICT)
    public static final String SOFTWARE_CATEGORY_IN_USE = "Software_SOFTWARE_CATEGORY_IN_USE";

    /** 同一分类下已存在同名软件 */
    @RsmInfo(template = "该分类下已存在同名软件", status = HttpStatus.CONFLICT)
    public static final String SOFTWARE_NAME_EXISTS = "Software_SOFTWARE_NAME_EXISTS";

    /** 按 id 查版本但不存在 */
    @RsmInfo(template = "软件版本不存在", status = HttpStatus.NOT_FOUND)
    public static final String SOFTWARE_VERSION_NOT_FOUND = "Software_SOFTWARE_VERSION_NOT_FOUND";

    /** 同一软件下版本号 + 平台重复 */
    @RsmInfo(template = "该软件下已存在相同版本号和平台的版本", status = HttpStatus.CONFLICT)
    public static final String SOFTWARE_VERSION_DUPLICATED = "Software_SOFTWARE_VERSION_DUPLICATED";

    /** 版本已停用，不能下载 */
    @RsmInfo(template = "该版本已停用，无法下载", status = HttpStatus.CONFLICT)
    public static final String SOFTWARE_VERSION_DISABLED = "Software_SOFTWARE_VERSION_DISABLED";

    /**
     * 平台取值非法。
     * <p>
     * 筛选时也拒绝而不是忽略: 忽略的表现是返回全部软件, 界面看起来筛了, 实际没筛。
     */
    @RsmInfo(template = "平台只能是 WINDOWS、MACOS、LINUX 或 COMMON", status = HttpStatus.BAD_REQUEST)
    public static final String SOFTWARE_PLATFORM_INVALID = "Software_SOFTWARE_PLATFORM_INVALID";

    /** 文件信息不完整，或地址不是 /file/ 下的同源地址 */
    @RsmInfo(template = "文件信息不完整或地址非法", status = HttpStatus.BAD_REQUEST)
    public static final String SOFTWARE_FILE_INVALID = "Software_SOFTWARE_FILE_INVALID";

    /** 编辑时被他人抢先修改（乐观锁冲突） */
    @RsmInfo(template = "软件已被他人修改，请刷新后重试", status = HttpStatus.CONFLICT)
    public static final String SOFTWARE_CONCURRENT_MODIFIED = "Software_SOFTWARE_CONCURRENT_MODIFIED";

    /** 下载计数时发现版本刚好被停用或删除 */
    @RsmInfo(template = "该版本当前不可下载，请刷新后重试", status = HttpStatus.CONFLICT)
    public static final String SOFTWARE_DOWNLOAD_UNAVAILABLE = "Software_SOFTWARE_DOWNLOAD_UNAVAILABLE";

    /** 成功键：下载地址已准备好 */
    @RsmInfo(template = "开始下载", status = HttpStatus.OK)
    public static final String DOWNLOAD_STARTED = "Software_DOWNLOAD_STARTED";
}
