package com.github.gaohongf.auth.entity.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理端封禁 / 解封用户。{@code true} 为封禁，{@code false} 为解封。 */
@Data
public class UpdateUserLockedCommand {
    @NotNull(message = "请指定封禁状态")
    private Boolean locked;
}
