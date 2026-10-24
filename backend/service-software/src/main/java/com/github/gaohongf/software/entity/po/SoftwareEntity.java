package com.github.gaohongf.software.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 软件目录项（不含具体版本）。
 *
 * <p>
 * 厂商放在这里而不是每个版本上：一个产品的厂商不随版本变化，放在版本上会变成每次都要重填、
 * 并且迟早出现同一软件两个厂商的脏数据。
 */
@EqualsAndHashCode(callSuper = false)
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "software", autoResultMap = true)
public class SoftwareEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 软件名称，同一分类下不能重复 */
    private String name;

    /** 厂商 / 发布方 */
    private String vendor;

    private Long categoryId;

    /** 简介 */
    private String description;

    /**
     * 是否上架。
     * <p>
     * 停用后下载页（列表、详情、下载）都不可见；管理页仍然可见，便于重新启用。
     */
    private Boolean enabled;
}
