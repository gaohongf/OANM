package com.github.gaohongf.auth.entity.po.intermediate;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data 
@EqualsAndHashCode(callSuper = false)
@TableName("role_permissions")
public class RolePermissionEntity extends BaseEntity{
    private String roleName;
    private Long permissionId;
}
