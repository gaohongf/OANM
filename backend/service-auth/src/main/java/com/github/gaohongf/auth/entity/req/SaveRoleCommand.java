package com.github.gaohongf.auth.entity.req;

import org.hibernate.validator.constraints.Length;

import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增/修改角色。
 * <p>
 * {@code roleName} 只在新增时使用: 它是 {@code roles} 表的主键, 且被
 * {@code user_roles} / {@code role_permissions} 以外键引用, 改名等于改主键 + 级联两处。
 * 修改接口会忽略这个字段, 而不是报错 —— 接口上不提供改名能力比提供一个再拒绝更清楚。
 */
@Data
public class SaveRoleCommand {

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 2, max = 64, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String roleName;

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 64, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String label;

    // 刻意没有 check(): 这个命令没有跨字段规则, 上面两个注解已经完整覆盖了非空与长度。
    // 既有约定是在 Command 里再写一个手写 check(), 但那会带来两个问题:
    //   1. 两处约束各自演化 —— CreateUserCommand 就是 @Length(max=6) 和 check() 的 max=10 打架
    //   2. 借 BaseValidationRsm 的插值模板当业务消息会导致消息格式化失败（表现为 500 而不是 400）
    // 所以 check() 只保留给注解表达不了的跨字段规则, 见 SaveMenuCommand / SaveApiRouteCommand。
}
