package com.github.gaohongf.auth.interceptor;


import org.springframework.lang.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.github.gaohongf.auth.annotation.IsOpen;
import com.lingyun.base.rsm.HttpStatusRsm;
import com.lingyun.base.rsm.R;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthorizationInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {
        if (handler instanceof HandlerMethod handlerMethod) {
            IsOpen isOpen = handlerMethod.getMethodAnnotation(IsOpen.class);
            if (isOpen != null) {
                return true;
            }
        }

        String method = request.getMethod();
        String requestURI = request.getRequestURI();
        if (StpUtil.hasPermission(method + ":" + requestURI)) {
            return true;
        }
        return R.error(HttpStatusRsm.FORBIDDEN);
    }
}
