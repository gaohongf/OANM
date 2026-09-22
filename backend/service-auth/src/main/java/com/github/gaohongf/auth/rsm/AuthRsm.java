package com.github.gaohongf.auth.rsm;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.lingyun.base.rsm.RsmManager;
import com.lingyun.base.rsm.annotation.RsmInfo;

@Component 
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

}
