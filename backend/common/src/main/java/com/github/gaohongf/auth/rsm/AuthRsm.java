package com.github.gaohongf.auth.rsm;

import org.springframework.http.HttpStatus;

import com.lingyun.base.rsm.RsmManager;
import com.lingyun.base.rsm.annotation.RsmInfo;

/**
 * 认证鉴权相关的统一响应消息。
 *
 * <h2>不要给它加 @Component</h2>
 * 它由 {@code com.github.gaohongf.auth.config.AuthorizationAutoConfiguration} 注册。
 * 之前它是 {@code @Component}, 而它的包 {@code com.github.gaohongf.auth.rsm} 只有 service-auth
 * 才扫得到（各服务的启动类在 {@code com.github.gaohongf.<服务名>} 下）。
 * <p>
 * 后果非常隐蔽: 其他服务里 {@code R.error(AuthRsm.NOT_LOGIN)} 拿到的是一个<b>没注册过的消息键</b>,
 * RSM 解析不出来就退回默认消息, 于是"鉴权失败"被渲染成 {@code {"code":1016,"msg":"成功"}} ——
 * 拒绝被报成了成功, 而且不抛错、不告警。
 */
public class AuthRsm implements RsmManager{
    
    // ---- 认证 ----
    /** 用户登录时密码验证失败 */
    @RsmInfo(template = "密码错误", status = HttpStatus.UNAUTHORIZED)
    public static final String PASSWORD_ERROR = "Authentication_PASSWORD_ERROR";

    /** 登录时输入的用户名在系统中不存在 */
    @RsmInfo(template = "用户不存在", status = HttpStatus.UNAUTHORIZED)
    public static final String USER_DOES_NOT_EXIST = "Authentication_USER_DOES_NOT_EXIST";

    /** 登录时遇到无法识别的用户类型（如多态登录逻辑中未覆盖的 User 子类） */
    @RsmInfo(template = "未知的用户类型", status = HttpStatus.UNAUTHORIZED)
    public static final String UNKNOWN_USER_TYPE = "Authentication_UNKNOWN_USER_TYPE";

    /** 用户登出成功 */
    @RsmInfo(template = "登出成功", status = HttpStatus.OK)
    public static final String LOGOUT_SUCCESS = "Authentication_LOGOUT_SUCCESS";

    /** 用户登录成功 */
    @RsmInfo(template = "登入成功", status = HttpStatus.OK)
    public static final String LOGIN_SUCCESS = "Authentication_LOGIN_SUCCESS";

    /** 登出操作执行失败（如 Token 已失效或服务端错误） */
    @RsmInfo(template = "登出失败", status = HttpStatus.INTERNAL_SERVER_ERROR)
    public static final String LOGOUT_FAIL = "Authentication_LOGOUT_FAIL";

    /** 登录操作执行失败 */
    @RsmInfo(template = "登录失败", status = HttpStatus.INTERNAL_SERVER_ERROR)
    public static final String LOGIN_FAIL = "Authentication_LOGIN_FAIL";

    /** 用户访问需要认证的端点时未提供有效的登录 Token */
    @RsmInfo(template = "未登录", status = HttpStatus.UNAUTHORIZED)
    public static final String NOT_LOGIN = "Authentication_NOT_LOGIN";

    // ---- JWT ----
    /** JWT Token 格式异常或解析过程中发生不可恢复的错误 */
    @RsmInfo(template = "异常的登录凭证", status = HttpStatus.FORBIDDEN)
    public static final String EXCEPTION_JWT = "Authentication_EXCEPTION_JWT";

    /** JWT Token 已超过有效期 */
    @RsmInfo(template = "登录凭证已失效", status = HttpStatus.FORBIDDEN)
    public static final String JWT_EXPIRED = "Authentication_JWT_EXPIRED";

    /** JWT Token 内容非法（签名错误、被篡改等） */
    @RsmInfo(template = "非法的凭证", status = HttpStatus.FORBIDDEN)
    public static final String INVALID_JWT = "Authentication_INVALID_JWT";

    // ---- 验证码 ----
    /** 图片验证码或数学验证码生成过程中发生错误 */
    @RsmInfo(template = "验证码生成失败", status = HttpStatus.INTERNAL_SERVER_ERROR)
    public static final String CAPTCHA_GENERATION_FAILED = "Authentication_CAPTCHA_GENERATION_FAILED";

    /** 用户提交的验证码与服务器存储的预期值不符 */
    @RsmInfo(template = "验证码校验失败", status = HttpStatus.UNAUTHORIZED)
    public static final String CAPTCHA_VERIFICATION_FAILED = "Authentication_CAPTCHA_VERIFICATION_FAILED";

    /** 验证码已超过有效时间，需要重新获取 */
    @RsmInfo(template = "验证码已经过期请重新获取", status = HttpStatus.UNAUTHORIZED)
    public static final String CAPTCHA_EXPIRED = "Authentication_CAPTCHA_EXPIRED";

    // ---- 凭证 ----
    /** 凭证处理方式配置错误（如未知的认证器类型） */
    @RsmInfo(template = "错误的凭证处理方式", status = HttpStatus.INTERNAL_SERVER_ERROR)
    public static final String INCORRECT_VOUCHER_PROCESSING_METHOD = "Authentication_INCORRECT_VOUCHER_PROCESSING_METHOD";

    /** 系统无法识别用户提交的凭证类型（如既不是密码也不是邮件验证码） */
    @RsmInfo(template = "无法识别的凭证", status = HttpStatus.UNAUTHORIZED)
    public static final String UNRECOGNIZED_CREDENTIALS = "Authentication_UNRECOGNIZED_CREDENTIALS";

    // ---- 账号状态 ----
    /** 用户账号已被管理员停用 */
    @RsmInfo(template = "账号已停用", status = HttpStatus.UNAUTHORIZED)
    public static final String ACCOUNT_DISABLED = "Authentication_ACCOUNT_DISABLED";

    /** 用户账号已被锁定（如密码错误次数过多） */
    @RsmInfo(template = "账号已锁定", status = HttpStatus.UNAUTHORIZED)
    public static final String ACCOUNT_LOCKED = "Authentication_ACCOUNT_LOCKED";

    /** 注册时账号已存在 */
    @RsmInfo(template = "账户已存在", status = HttpStatus.BAD_REQUEST)
    public static final String ACCOUNT_EXISTS = "Authentication_ACCOUNT_EXISTS";
    // ---- 资源 ----
    /** 请求的 API 路径未被认证系统收录，可能是新增的 Controller 未重启服务导致 */
    @RsmInfo(template = "未被认证系统正确收录的资源 {0}", status = HttpStatus.INTERNAL_SERVER_ERROR)
    public static final String UNAUTHENTICATED_RESOURCE = "Authentication_UNAUTHENTICATED_RESOURCE";

    /** 一个请求路径匹配到多个资源定义导致歧义（如同一路径匹配了两个模式评分相同的 HandlerMethod） */
    @RsmInfo(template = "路径同时指向了多处资源", status = HttpStatus.INTERNAL_SERVER_ERROR)
    public static final String PATH_POINTS_TO_MULTIPLE_RESOURCES = "Authentication_PATH_POINTS_TO_TWO_RESOURCES";

    /** 按 id 找不到用户（管理端的操作, 与登录时的 USER_DOES_NOT_EXIST 区分开: 那是 401, 这是 404） */
    @RsmInfo(template = "用户不存在", status = HttpStatus.NOT_FOUND)
    public static final String USER_NOT_FOUND = "Authentication_USER_NOT_FOUND";

    // ---- 权限管理 ----
    /** 按 id 找不到权限 */
    @RsmInfo(template = "权限不存在", status = HttpStatus.NOT_FOUND)
    public static final String PERMISSION_NOT_FOUND = "Authentication_PERMISSION_NOT_FOUND";

    /** 新增权限时 permission_key 已存在 */
    @RsmInfo(template = "权限标识已存在", status = HttpStatus.CONFLICT)
    public static final String PERMISSION_EXISTS = "Authentication_PERMISSION_EXISTS";

    /** 权限正被菜单引用, 不能删除 —— 否则菜单会失去门禁, 变成对所有人可见 */
    @RsmInfo(template = "该权限正被菜单引用, 请先解除引用再删除", status = HttpStatus.CONFLICT)
    public static final String PERMISSION_IN_USE = "Authentication_PERMISSION_IN_USE";

    /** 超级管理员权限(键为 * 的内置行)不允许改名或删除 */
    @RsmInfo(template = "内置的超级管理员权限不允许修改或删除", status = HttpStatus.BAD_REQUEST)
    public static final String SUPER_PERMISSION_IMMUTABLE = "Authentication_SUPER_PERMISSION_IMMUTABLE";

    /** 只有已经持有超级管理员权限的人才能把它授出去, 否则是提权 */
    @RsmInfo(template = "只有超级管理员可以授予超级管理员权限", status = HttpStatus.FORBIDDEN)
    public static final String GRANT_SUPER_PERMISSION_DENIED = "Authentication_GRANT_SUPER_PERMISSION_DENIED";

    // ---- 角色管理 ----
    /** 按角色名找不到角色 */
    @RsmInfo(template = "角色不存在", status = HttpStatus.NOT_FOUND)
    public static final String ROLE_NOT_FOUND = "Authentication_ROLE_NOT_FOUND";

    /** 新增角色时 role_name 已存在 */
    @RsmInfo(template = "角色名已存在", status = HttpStatus.CONFLICT)
    public static final String ROLE_EXISTS = "Authentication_ROLE_EXISTS";

    /** 角色已被用户持有, 不能删除 */
    @RsmInfo(template = "该角色已分配给用户, 请先解除分配再删除", status = HttpStatus.CONFLICT)
    public static final String ROLE_IN_USE = "Authentication_ROLE_IN_USE";

    /** role_name 是主键且被两张关联表以外键引用, 不支持改名 */
    @RsmInfo(template = "角色名是主键, 不支持修改", status = HttpStatus.BAD_REQUEST)
    public static final String ROLE_NAME_IMMUTABLE = "Authentication_ROLE_NAME_IMMUTABLE";

    // ---- 菜单管理 ----
    /** 按 id 找不到菜单 */
    @RsmInfo(template = "菜单不存在", status = HttpStatus.NOT_FOUND)
    public static final String MENU_NOT_FOUND = "Authentication_MENU_NOT_FOUND";

    /** 菜单下还有子节点, 不允许删除（不静默级联） */
    @RsmInfo(template = "该菜单下还有子菜单, 请先删除或移走子菜单", status = HttpStatus.CONFLICT)
    public static final String MENU_HAS_CHILDREN = "Authentication_MENU_HAS_CHILDREN";

    /** 把菜单挂到自己下面 */
    @RsmInfo(template = "不能把菜单挂到它自己下面", status = HttpStatus.BAD_REQUEST)
    public static final String MENU_PARENT_IS_SELF = "Authentication_MENU_PARENT_IS_SELF";

    /** 把菜单挂到自己的后代下面会形成环 */
    @RsmInfo(template = "不能把菜单挂到它自己的子菜单下面", status = HttpStatus.BAD_REQUEST)
    public static final String MENU_PARENT_IS_DESCENDANT = "Authentication_MENU_PARENT_IS_DESCENDANT";

    /** 祖先链/建树超过了最大深度。出现即说明库里已经有环, 是数据损坏而不是用户操作错误 */
    @RsmInfo(template = "菜单层级异常(可能已存在环), 请检查数据", status = HttpStatus.INTERNAL_SERVER_ERROR)
    public static final String MENU_TREE_TOO_DEEP = "Authentication_MENU_TREE_TOO_DEEP";

    // ---- 菜单管理（跨字段规则, 注解表达不了） ----
    /**
     * 配了 component（是"页面"）却没有 path。
     * <p>
     * 这种数据在前端注册不了路由, 点开就是白屏, 而且它在库里看起来完全正常,
     * 所以宁可在写入时就拒掉。
     */
    @RsmInfo(template = "配置了页面组件的菜单必须填写前端路由", status = HttpStatus.BAD_REQUEST)
    public static final String MENU_PAGE_REQUIRES_PATH = "Authentication_MENU_PAGE_REQUIRES_PATH";

    // ---- 路由管理 ----
    /** 按 id 找不到路由 */
    @RsmInfo(template = "路由不存在", status = HttpStatus.NOT_FOUND)
    public static final String API_ROUTE_NOT_FOUND = "Authentication_API_ROUTE_NOT_FOUND";

    /** 新增路由时 path_pattern 已存在 */
    @RsmInfo(template = "路径前缀已被其他路由使用", status = HttpStatus.CONFLICT)
    public static final String API_ROUTE_PATH_EXISTS = "Authentication_API_ROUTE_PATH_EXISTS";

    /**
     * 路径前缀没有以 {@code /} 开头。
     * <p>
     * 少了这道校验, {@code "api/ops/**"} 这种写法也能存进库, 而网关的 PathPattern
     * 解析不了它 —— 症状是那条路由<b>静默不生效</b>, 比报错难查得多。
     */
    @RsmInfo(template = "路径前缀必须以 / 开头", status = HttpStatus.BAD_REQUEST)
    public static final String API_ROUTE_PATH_MUST_BE_ABSOLUTE = "Authentication_API_ROUTE_PATH_MUST_BE_ABSOLUTE";

}
