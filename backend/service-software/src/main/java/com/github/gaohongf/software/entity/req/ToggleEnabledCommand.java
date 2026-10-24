package com.github.gaohongf.software.entity.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 启用 / 停用。只带这一个字段，避免把整个表单当成补丁传上来 */
@Data
public class ToggleEnabledCommand {

    @NotNull(message = "请指定启用状态")
    private Boolean enabled;
}
