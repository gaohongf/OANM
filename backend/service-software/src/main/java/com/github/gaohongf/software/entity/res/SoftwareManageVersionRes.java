package com.github.gaohongf.software.entity.res;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 管理端版本行。
 *
 * <p>
 * 带 fileUrl / fileId：编辑表单要显示"当前文件"，不重新上传时原样保留。
 * 这些字段只出现在需要管理权限的响应里。
 */
@Data
public class SoftwareManageVersionRes {

    private String id;

    private String versionNo;

    private String platform;

    private String releaseNote;

    private String fileId;

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private Long downloadCount;

    private Boolean enabled;

    private LocalDateTime createTime;
}
