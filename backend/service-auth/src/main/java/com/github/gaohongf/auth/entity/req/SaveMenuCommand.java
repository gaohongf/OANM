package com.github.gaohongf.auth.entity.req;

import org.hibernate.validator.constraints.Length;

import com.github.gaohongf.auth.rsm.AuthRsm;
import com.lingyun.base.rsm.R;
import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增/修改菜单节点。
 * <p>
 * {@code parentId} 为 {@code null} 表示顶级。修改时如果改变了 {@code parentId}, 相当于移动节点,
 * 服务层会校验不会成环。
 */
@Data
public class SaveMenuCommand {

    /**
     * 父节点 id; {@code null} 表示顶级。
     * <p>
     * 不标 {@code @NotNull} —— 顶级节点本来就该传 null。
     */
    private Long parentId;

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 64, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String name;

    /**
     * 前端路由, 如 {@code /ops/work-order}。
     * <p>
     * 目录节点可以为空（纯分组用, 不产生路由）, 所以规则是"要么是目录、要么必须有 path",
     * 这个交叉约束在 {@link #check()} 里表达, 注解表达不了。
     */
    @Length(max = 255, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String path;

    /**
     * 前端页面标识, 相对前端 {@code src/pages/} 的路径, 如 {@code ops/work-order/index}。
     * <p>
     * 为空即"目录", 非空即"页面"—— 这个区分是可推导的, 所以没有单独的节点类型字段。
     */
    @Length(max = 128, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String component;

    @Length(max = 64, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String icon;

    private Integer sort;

    private Boolean hidden;

    private Boolean keepAlive;

    /** 所需权限 id; {@code null} 表示登录即可见 */
    private Long permissionId;

    private Boolean enabled;

    /**
     * 只放 bean validation 注解<b>表达不了</b>的跨字段规则 —— 长度与非空已由上面的注解覆盖,
     * 在这里重复一遍只会让两处约束各自演化并最终打架, 而且借
     * {@code BaseValidationRsm} 的插值模板当业务消息会导致消息格式化失败（500）。
     * 详见 {@code SaveApiRouteCommand#check} 的同类说明。
     */
    public void check() {
        // 配了 component（是"页面"）却没填 path: 这种数据在前端注册不了路由, 点开就是白屏,
        // 而它在库里看起来完全正常 —— 所以宁可在写入时就拒掉。
        if (component != null && !component.isBlank() && (path == null || path.isBlank())) {
            R.error(AuthRsm.MENU_PAGE_REQUIRES_PATH);
        }
    }
}
