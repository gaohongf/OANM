package com.github.gaohongf.knowledge.entity.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 发布 / 取消发布。只带这一个字段，避免把整个正文当成补丁传上来 */
@Data
public class ChangeStatusCommand {

    @NotBlank(message = "请指定文章状态")
    private String status;
}
