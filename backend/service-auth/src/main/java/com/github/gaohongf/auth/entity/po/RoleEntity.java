package com.github.gaohongf.auth.entity.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("roles")
@Data 
@EqualsAndHashCode(callSuper = false)
public class RoleEntity extends BaseEntity{
    /**
     * 角色名-唯一主键
     * @example `admin`, `user`
     */
    @TableId(value = "role_name")
    private String roleName;
    /**
     * 标签
     * @example `管理员`, `用户`
     */
    private String label;
}
