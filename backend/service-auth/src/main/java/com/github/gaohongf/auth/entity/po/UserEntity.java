package com.github.gaohongf.auth.entity.po;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
@AllArgsConstructor 
@NoArgsConstructor 
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("users")
public class UserEntity extends BaseEntity{
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 用户名 （唯一）
     */
    private String username;
    /**
     * 昵称 （可重复）
     */
    private String nickname;
    /**
     * 密码
     */
    private String password;
    /**
     * 有没有被封号
     */
    private Boolean locked;
}
