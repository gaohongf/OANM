package com.github.gaohongf.auth.entity.res;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网关路由（管理端视图）。
 * <p>
 * 注意这里<b>没有</b>权限字段: 路由的粒度是服务前缀, 一个前缀不对应单一权限。
 * 端点级的权限在 {@code permissions} 表里, 键形如 {@code GET:/api/ops/work_order/{id}}。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiRouteRes {

    private Long id;

    /** 备注名 */
    private String name;

    /** 路径前缀, 如 {@code /api/ops/**} */
    private String pathPattern;

    /** 目标服务, 如 {@code lb://service-work-order} */
    private String uri;

    /** 优先级, 越小越先匹配 */
    private Integer routeOrder;

    private Boolean enabled;

    /** 人工锁定; 前端的编辑界面据此提示"自注册不会覆盖这一条" */
    private Boolean locked;

    /** MANUAL / REGISTERED */
    private String source;
}
