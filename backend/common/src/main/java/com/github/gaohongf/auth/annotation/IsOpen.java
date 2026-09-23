package com.github.gaohongf.auth.annotation;

import java.lang.annotation.*;

/**
 * 标记一个 API 端点无需认证即可访问。
 * <p>
 * 用于 Controller 方法上，认证过滤器识别此注解后跳过身份校验。
 * 属于认证授权模块的核心契约，消费项目通过此注解声明公开端点。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IsOpen {
}
