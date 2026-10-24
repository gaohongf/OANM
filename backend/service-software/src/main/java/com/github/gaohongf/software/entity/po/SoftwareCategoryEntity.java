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
 * 软件分类。目前只有一级，name 的重复由部分唯一索引兜底。
 */
@EqualsAndHashCode(callSuper = false)
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "software_categories", autoResultMap = true)
public class SoftwareCategoryEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分类名，同一时刻不能重复 */
    private String name;

    /** 展示顺序，小的在前 */
    private Integer sort;
}
