package com.github.gaohongf.auth.register;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.AntPathMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.gaohongf.auth.client.ApiResponse;
import com.github.gaohongf.auth.client.RegistrationClient;
import com.github.gaohongf.auth.mq.model.RegisterServiceAuth;
import com.github.gaohongf.mq.KafkaTopics;

import lombok.extern.slf4j.Slf4j;

/**
 * 启动时把本服务的端点上报给 service-auth，并自检它们有没有网关路由可达。
 *
 * <h2>为什么用 Kafka 为主、HTTP 为兜底</h2>
 * 上报<b>丢不起</b>：漏一个端点，那个端点就永远没有权限行，任何角色都无法被授予它 ——
 * 现象只是所有人都 403，看不出跟"登记"有关。而服务启动时 service-auth 可能正好不可用。
 * <ul>
 *   <li>Kafka：消息留在 broker 上，service-auth 起来后照样能消费到。<b>能容忍对端不在。</b></li>
 *   <li>HTTP：立刻知道结果，但失败就失败。作为 Kafka 发送失败时的即时兜底，
 *       总比什么都不做要好。</li>
 * </ul>
 * 两者都调同一个幂等登记逻辑，所以同时成功也无害。
 */
@Slf4j
public class ServiceRegistrar {

    /**
     * 单步的上限。
     * <p>
     * 这个流程跑在启动路径上（{@code ApplicationReadyEvent}），每一步都必须能快速失败，
     * 否则基础设施有问题时会让人觉得"这个服务起不来了"。三步加起来最坏 9 秒左右。
     */
    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(3);

    private final ServiceEndpointsScanner serviceEndpointsScanner;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RegistrationClient registrationClient;
    private final ObjectMapper objectMapper;
    private final String instanceId;

    public ServiceRegistrar(ServiceEndpointsScanner serviceEndpointsScanner,
                            KafkaTemplate<String, String> kafkaTemplate,
                            RegistrationClient registrationClient,
                            ObjectMapper objectMapper,
                            String instanceId) {
        this.serviceEndpointsScanner = serviceEndpointsScanner;
        this.kafkaTemplate = kafkaTemplate;
        this.registrationClient = registrationClient;
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
    }

    /**
     * 启动时执行一次。
     * <p>
     * 用 {@code ApplicationReadyEvent} 而不是更早的时机：那时 Web 容器已在监听、所有 Bean
     * 都已就绪，扫到的端点清单才完整（包含所有 {@code @Controller}）。
     * {@code ApplicationRunner} 也在 refresh 之后，两者等价。
     * <p>
     * 任何一步失败都只记日志，<b>绝不抛出去打断启动</b> —— 上报失败是"少了一条权限行"，
     * 让服务起不来则是"整个服务不可用"，后者严重得多。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        ServiceEndpoints endpoints = serviceEndpointsScanner.scan();
        if (endpoints.endpoints().isEmpty()) {
            log.info("本服务没有需要权限的端点，跳过自注册");
            return;
        }

        RegisterServiceAuth payload = new RegisterServiceAuth(instanceId, endpoints.permissionKeys());

        if (publishViaKafka(payload)) {
            log.info("已向 Kafka 上报 {} 个端点权限键，由 service-auth 异步登记", payload.getAuths().size());
        } else {
            registerViaHttp(payload);
        }

        // 自检与登记是两件事：登记解决"能不能被授予"，自检解决"从外部能不能访问"。
        // 后者即使登记成功也可能不成立（端点所在的路径前缀没有对应的网关路由）。
        selfCheckGatewayRoutes(endpoints);
    }

    private boolean publishViaKafka(RegisterServiceAuth payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(KafkaTopics.SERVICE_REGISTRATION, instanceId, json)
                    .get(STEP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            // 恢复中断标志，否则这个线程的中断状态被吞掉，上层再也无法感知
            Thread.currentThread().interrupt();
            log.warn("上报端点的过程中被中断，将改用 HTTP 兜底");
            return false;
        } catch (Exception failure) {
            log.warn("向 Kafka 上报端点失败，将改用 HTTP 兜底。"
                    + "常见原因: broker 不可用、话题不存在、序列化失败。", failure);
            return false;
        }
    }

    private void registerViaHttp(RegisterServiceAuth payload) {
        try {
            ApiResponse<List<String>> response = registrationClient.register(payload);
            if (response == null) {
                throw new IllegalStateException("响应为空");
            }
            response.requireSuccess("上报端点");
            List<String> created = response.data() == null ? List.of() : response.data();
            log.info("已通过 HTTP 兜底上报 {} 个端点，其中新建 {} 条权限",
                    payload.getAuths().size(), created.size());
        } catch (Exception failure) {
            // 两条路都失败 = 这次上报丢了。不抛异常（不能因此让服务起不来），但要 ERROR 级 ——
            // 后果是"这些端点没有任何角色能被授予"，而现象只是 403，必须留下明确的线索。
            log.error("端点自注册失败（Kafka 与 HTTP 都不通）。"
                    + "后果: 本服务的这些端点可能没有对应的权限行，任何角色都无法被授予它们，"
                    + "表现为所有人都访问不了。可稍后通过 POST {} 手动补报。",
                    com.github.gaohongf.auth.client.AuthInternalApi.REGISTER, failure);
        }
    }

    /**
     * 自检：本服务的每个端点，是否至少被一条网关路由覆盖。
     * <p>
     * 不被覆盖的端点从外部访问会返回 <b>404</b>（网关没有匹配的路由），而直连服务时一切正常 ——
     * 不主动报出来，排查方向很容易跑到服务内部去。
     * <p>
     * 匹配用 {@link AntPathMatcher}：路由是前缀式模式（{@code /api/ops/**}），
     * 端点是具体模式（{@code /api/ops/work_order/{id}}），{@code **} 能匹配后者整串。
     * 方向不能反 —— 反过来（拿端点去匹配路由）会漏掉 {@code **} 的语义。
     */
    private void selfCheckGatewayRoutes(ServiceEndpoints endpoints) {
        List<String> routePatterns;
        try {
            ApiResponse<List<String>> response = registrationClient.routePatterns();
            if (response == null) {
                throw new IllegalStateException("响应为空");
            }
            response.requireSuccess("查询网关路由");
            routePatterns = response.data() == null ? List.of() : response.data();
        } catch (Exception failure) {
            // 自检本身失败不产生任何后果（端点该怎么用还怎么用），所以只到 WARN
            log.warn("无法获取网关路由，跳过启动自检", failure);
            return;
        }

        if (routePatterns.isEmpty()) {
            log.warn("网关当前没有任何启用中的路由，本服务的所有端点从外部都无法访问。"
                    + "请在 service-auth 的 api_routes 里为它们配置路由。");
            return;
        }

        AntPathMatcher matcher = new AntPathMatcher();
        List<String> uncovered = endpoints.endpoints().stream()
                .map(ServiceEndpoints.Endpoint::pattern)
                .distinct()
                .filter(pattern -> routePatterns.stream().noneMatch(route -> matcher.match(route, pattern)))
                .toList();

        if (!uncovered.isEmpty()) {
            log.warn("以下 {} 个端点没有任何网关路由能到达，从外部访问会返回 404（直连本服务则正常）。"
                            + "请在 service-auth 的 api_routes 里补一条覆盖它们的路由。当前路由前缀: {}。未覆盖: {}",
                    uncovered.size(), routePatterns, uncovered);
        } else {
            log.info("启动自检通过: {} 个端点都有网关路由覆盖", endpoints.endpoints().size());
        }
    }
}
