package com.github.gaohongf.serializer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// RECORD_COMPONENT: 记录类上直接写 @User Long createBy 时, 注解落的是组件而不是字段/方法
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface User {
    boolean username() default true;
    boolean nickname() default false;
    boolean id() default true;
}
