package com.github.gaohongf.software.entity.res;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 下载页看到的版本信息。
 *
 * <p>
 * 刻意<b>不含 fileUrl</b>：地址只由下载接口按次返回。公开静态地址本身不是秘密，
 * 但没必要在每个列表/详情响应里都印出来。
 */
@Data
public class SoftwareVersionRes {

    private String id;

    private String versionNo;

    /** WINDOWS / MACOS / LINUX / COMMON */
    private String platform;

    private String releaseNote;

    /** 上传时的原始文件名，用于下载时展示与还原 */
    private String fileName;

    private Long fileSize;

    private Long downloadCount;

    private LocalDateTime createTime;
}
