package com.github.gaohongf.software.entity.res;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 管理端详情 = 管理列表行（已含简介） + 全部未删除版本（含停用） */
@EqualsAndHashCode(callSuper = true)
@Data
public class SoftwareManageDetailRes extends SoftwareManageRes {

    private List<SoftwareManageVersionRes> versions;
}
