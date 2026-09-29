package com.github.gaohongf.auth.entity.req;

import org.hibernate.validator.constraints.Length;

import com.github.gaohongf.auth.rsm.AuthRsm;
import com.lingyun.base.rsm.R;
import com.lingyun.base.rsm.validation.BaseValidationRsm;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增/修改网关路由。
 */
@Data
public class SaveApiRouteCommand {

    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 64, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String name;

    /**
     * 路径前缀, 如 {@code /api/ops/**}。
     * <p>
     * 必须是前缀形态而不是具体路径: 路由的粒度是服务, 端点级的控制由权限负责。
     * 详见 {@code ApiRouteEntity} 的类注释。
     */
    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 255, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String pathPattern;

    /** 目标服务, 如 {@code lb://service-work-order} */
    @NotBlank(message = BaseValidationRsm.JAKARTA_VALIDATION_CONSTRAINTS_NOTBLANK_MESSAGE)
    @Length(min = 1, max = 255, message = BaseValidationRsm.ORG_HIBERNATE_VALIDATOR_CONSTRAINTS_LENGTH_MESSAGE)
    private String uri;

    private Integer routeOrder;

    private Boolean enabled;

    /**
     * 只放 bean validation 注解<b>表达不了</b>的规则。
     *
     * <h2>为什么不在这里重复长度约束</h2>
     * 上面那些 {@code @NotBlank}/{@code @Length} 已经覆盖了非空与长度, 在 {@code check()} 里
     * 再写一遍有两个害处:
     * <ol>
     *   <li>两处约束会各自演化, 最后互相矛盾 —— 项目里 {@code CreateUserCommand} 就是
     *       {@code @Length(max = 6)} 和 {@code check()} 的 {@code max = 10} 打架</li>
     *   <li>借 {@code BaseValidationRsm} 的常量当业务消息是<b>误用</b>: 那些文本是给
     *       Hibernate Validator 插值用的, 自带 {@code {min}} / {@code {regexp}} 这类占位符。
     *       拿它们调 {@code R.error(key, args...)} 会让消息格式化失败, 异常升级成 500
     *       （本类最初的写法就踩了这个坑: 非法前缀返回的是"服务器错误"而不是 400）</li>
     * </ol>
     * 跨字段规则没有注解能表达, 所以只能留在这里, 并且用 {@code AuthRsm} 里自己的消息键。
     */
    public void check() {
        if (!pathPattern.startsWith("/")) {
            R.error(AuthRsm.API_ROUTE_PATH_MUST_BE_ABSOLUTE);
        }
    }
}
