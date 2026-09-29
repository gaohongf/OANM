package com.github.gaohongf.auth.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增/修改权限。
 * <p>
 * {@code permissionKey} <b>可以</b>修改（内置的 {@code *} 那行除外）—— 菜单是通过
 * {@code permission_id} 外键引用权限的, 不是按字符串, 所以改名不会让菜单失去门禁。
 * 这正是当初选外键而不是字符串的理由。
 */
@Data
public class SavePermissionCommand {

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 255, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String permissionKey;

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 64, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String label;

    // 刻意没有 check(): 这个命令没有跨字段规则, 上面两个注解已经完整覆盖非空与长度。
    // 理由见 SaveRoleCommand 里的同类说明。
}
