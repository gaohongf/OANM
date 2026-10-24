package com.github.gaohongf.software.entity.res;

import lombok.Data;

/** 软件分类 */
@Data
public class SoftwareCategoryRes {

    /** 雪花 id 以字符串出网，避免 JS Number 丢精度 */
    private String id;

    private String name;

    private Integer sort;
}
