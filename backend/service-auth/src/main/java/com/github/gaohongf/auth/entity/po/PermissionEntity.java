package com.github.gaohongf.auth.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 权限表
 */
@Data
@EqualsAndHashCode(callSuper = false) 
@TableName("permissions")
public class PermissionEntity extends BaseEntity {
    /**
     * 唯一编号 避免使用permissionKey直接泄露
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 权限标识（唯一）
     */
    private String permissionKey;
    /**
     * 标签
     */
    private String label;
}
