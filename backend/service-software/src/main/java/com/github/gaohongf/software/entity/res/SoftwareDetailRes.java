package com.github.gaohongf.software.entity.res;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 下载页详情 = 列表行 + 简介 + 启用中的版本。
 *
 * <p>
 * 版本按创建时间倒序，前端按平台分组后，每组第一条就是该平台的最新版。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SoftwareDetailRes extends SoftwareRes {

    private String description;

    private List<SoftwareVersionRes> versions;
}
