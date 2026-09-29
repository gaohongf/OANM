package com.github.gaohongf.auth.service;

import java.util.List;

import com.github.gaohongf.auth.entity.req.SaveApiRouteCommand;
import com.github.gaohongf.auth.entity.res.ApiRouteRes;
import com.github.gaohongf.auth.res.GatewayRouteRes;

/**
 * 网关路由管理。
 */
public interface ApiRouteService {

    /** 全部路由（管理端视图, 含已停用的）。量级是每个服务一条, 不分页。 */
    List<ApiRouteRes> list();

    void create(SaveApiRouteCommand command);

    void update(Long id, SaveApiRouteCommand command);

    void delete(Long id);

    /**
     * 启用中的路由, 转成网关能直接反序列化的形状。
     * <p>
     * 给 {@code /auth/internal/routes} 用, 网关启动时和收到变更通知后各拉一次。
     */
    List<GatewayRouteRes> listEnabledAsGatewayRoutes();

    /**
     * 启用中的路由的路径模式, 如 {@code ["/api/ops/**"]}。
     * <p>
     * 给各服务的启动自检用: 判断"我的端点有没有网关路由能到达"。只需要模式,
     * 不需要完整的路由定义 —— 让调用方去解 {@code predicates[0].args} 是很别扭的接口。
     */
    List<String> listEnabledPathPatterns();
}
