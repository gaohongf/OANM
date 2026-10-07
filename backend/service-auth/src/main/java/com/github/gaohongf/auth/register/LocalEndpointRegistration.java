package com.github.gaohongf.auth.register;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import com.github.gaohongf.auth.service.ApiRouteService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * service-auth 自己的端点登记与自检 —— <b>走本地, 不经 Kafka 也不经 HTTP</b>。
 *
 * <h2>为什么不像其他服务那样用 {@code ServiceRegistrar}</h2>
 * 两个理由:
 * <ol>
 *   <li>数据就在本地。扫描完直接入库, 绕一圈消息队列再回到自己, 除了增加失败点没有任何好处。</li>
 *   <li>它<b>没有负载均衡器</b>（不需要），而 {@code RegistrationClient} 用的是 {@code lb://}
 *       地址, 一调就报 "No Feign Client for loadBalancing defined"。</li>
 * </ol>
 * 这与阶段一 {@code UserLookup} 的处理方式一致: 谁拥有数据, 谁就用本地实现。
 *
 * <h2>必须有它, 否则 service-auth 的管理接口全都授不出去</h2>
 * 管理端接口（菜单、角色、权限、路由的 CRUD）也"需要权限"。如果它们没有权限行,
 * 就没有任何角色能被授予它们 —— 结果是<b>只有持有 {@code *} 的超管能用</b>,
 * 而"权限管理员"这类角色根本配不出来。
 */
@Slf4j
@Component
@AllArgsConstructor
public class LocalEndpointRegistration {

    private final ServiceEndpointsScanner serviceEndpointsScanner;
    private final EndpointRegistrationService registrationService;
    private final ApiRouteService apiRouteService;

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        try {
            ServiceEndpoints endpoints = serviceEndpointsScanner.scan();
            if (endpoints.endpoints().isEmpty()) {
                log.info("service-auth 没有需要权限的端点，跳过本地登记");
                return;
            }

            List<String> created = registrationService.register(endpoints.permissionKeys());
            if (created.isEmpty()) {
                log.info("service-auth 的 {} 个端点权限均已登记过", endpoints.endpoints().size());
            } else {
                log.info("service-auth 本地登记新增 {} 条权限（共 {} 个端点）",
                        created.size(), endpoints.endpoints().size());
            }

            selfCheckGatewayRoutes(endpoints);
        } catch (Exception failure) {
            // 登记失败不该让服务起不来。但这里是本地调用, 失败基本只有数据库不可用一种可能,
            // 所以级别是 ERROR —— 说明环境已经有问题了。
            log.error("service-auth 本地端点登记失败。后果: 管理端接口可能没有对应的权限行, "
                    + "任何角色都无法被授予它们, 只有持有 * 的超管能用。", failure);
        }
    }

    /**
     * 自检: 自己的端点有没有网关路由覆盖。
     * <p>
     * 与其他服务的自检逻辑相同, 但这里直接问本地的 {@code ApiRouteService} ——
     * 数据就在本地, 没有理由发一次 HTTP 请求给自己。
     */
    private void selfCheckGatewayRoutes(ServiceEndpoints endpoints) {
        List<String> routePatterns = apiRouteService.listEnabledPathPatterns();

        if (routePatterns.isEmpty()) {
            log.warn("网关当前没有任何启用中的路由，本服务的所有端点从外部都无法访问。"
                    + "请在 api_routes 里补路由。");
            return;
        }

        AntPathMatcher matcher = new AntPathMatcher();
        List<String> uncovered = endpoints.endpoints().stream()
                .map(ServiceEndpoints.Endpoint::pattern)
                .distinct()
                .filter(pattern -> routePatterns.stream().noneMatch(route -> matcher.match(route, pattern)))
                .toList();

        if (uncovered.isEmpty()) {
            log.info("启动自检通过: {} 个端点都有网关路由覆盖", endpoints.endpoints().size());
        } else {
            log.warn("以下 {} 个端点没有任何网关路由能到达，从外部访问会返回 404（直连本服务则正常）。"
                            + "当前路由前缀: {}。未覆盖: {}",
                    uncovered.size(), routePatterns, uncovered);
        }
    }
}
