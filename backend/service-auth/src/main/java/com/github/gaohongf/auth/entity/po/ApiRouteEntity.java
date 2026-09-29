package com.github.gaohongf.auth.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.gaohongf.model.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 网关路由。**只负责转发, 不负责鉴权。**
 *
 * <h2>粒度是"服务前缀"</h2>
 * 一行 = 一个 {@code path_pattern} → 一个 {@code uri}（如 {@code /api/ops/**} →
 * {@code lb://service-work-order}）。端点级的权限信息不在这里, 而在 {@code permissions}
 * 表里, 键形如 {@code GET:/api/ops/work_order/{id}}。
 *
 * <h2>为什么刻意不把路由和权限合并到一张表</h2>
 * 合在一起会产生一种很难查的故障: 路由表少一行、或某行被停用, 表现是<b>网关 404</b>;
 * 而少一条权限数据表现是<b>服务 403</b>。前者把排查方向指到"路由配置"上, 而真正的问题
 * 在权限数据里。分开之后, 路由永远不会因为权限数据缺失而中断。
 *
 * <p>没有 {@code method} 列(前缀与方法无关)、没有 {@code permission_id} 列(一个前缀
 * 不对应单一权限)、没有 {@code filters} 列(按路径约定服务内路径 == 外部路径,
 * 不需要 StripPrefix)。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("api_routes")
public class ApiRouteEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 备注名, 如 "工单服务" */
    private String name;

    /** 路径前缀, 如 {@code /api/ops/**} */
    private String pathPattern;

    /** 目标服务, 如 {@code lb://service-work-order} */
    private String uri;

    /** 网关路由优先级, 越小越优先 */
    private Integer routeOrder;

    private Boolean enabled;

    /** 人工锁定; 为 true 时自注册不得覆盖 */
    private Boolean locked;

    /** 来源: MANUAL / REGISTERED */
    private String source;
}
