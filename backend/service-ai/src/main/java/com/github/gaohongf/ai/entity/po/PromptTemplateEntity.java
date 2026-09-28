package com.github.gaohongf.ai.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data 
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor 
@NoArgsConstructor 
@TableName("ai_prompt_template")
public class PromptTemplateEntity extends BaseEntity {
    /**
     * 唯一编号
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 提示词唯一编号
     * 可重复，配合版本号
     */
    private String uuid;
    private String title;
    private String template;
    private Integer promptVersion;
}
