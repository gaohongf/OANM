package com.github.gaohongf.auth.entity.po.intermediate;

import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data 
@EqualsAndHashCode(callSuper = false)
@TableName("user_roles")
public class UserRoleEntity extends BaseEntity {
    private Long userId;
    private String roleName;
}
