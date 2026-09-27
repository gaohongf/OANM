package com.github.gaohongf.wo.entity.po;

import org.apache.ibatis.type.EnumTypeHandler;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = false)
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "work_orders", autoResultMap = true)
public class WorkOrderEntity extends BaseEntity {
    /**
     * 编号
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 标题
     */
    private String title;
    /**
     * 经过AI进行润色与明确的问题描述
     */
    private String problemDescription;
    /**
     * 用户输入的原始问题描述
     */
    private String originalProblemDescription;
    /**
     * 受理人
     */
    private Long assigneeId;
    /**
     * 受理人对这个问题的笔记
     */
    private String assigneeNote;
    /**
     * 此单状态
     */
    @TableField(typeHandler = EnumTypeHandler.class)
    private WorkOrderStatus status;
}
