package com.github.gaohongf.auth.res;

import java.util.List;
import java.util.Map;

/**
 * 网关路由定义。这是 service-auth 与 api-gateway 之间的跨服务契约。
 *
 * <h2>字段为什么长这样（已从 jar 字节码核实, 不是照记忆写的）</h2>
 * 网关会把这个 JSON 直接反序列化成
 * {@code org.springframework.cloud.gateway.route.RouteDefinition}。那个类<b>没有任何
 * Jackson 注解</b>, 全靠 JavaBean 命名对应, 所以本 record 的<b>字段名必须逐字对齐</b>:
 * <ul>
 *   <li>{@code uri} 在网关侧是 {@code java.net.URI}（不是 String）——
 *       传 {@code "lb://service-work-order"} 这样的字符串即可, Jackson 有内置反序列化器</li>
 *   <li>{@code enabled} 在 4.3.5 里存在, 可以直接表达"停用"而不用从结果里剔除</li>
 *   <li>{@code order} 是 int, 越小的路由越先匹配</li>
 * </ul>
 * <p>
 * 网关侧<b>不需要</b>任何对应的 DTO 类 —— {@code RouteDefinition} 就在它的 classpath 上。
 * 也正因如此, 网关不能反向依赖本类（common 是 MVC 技术栈, 网关是 WebFlux,
 * 混在一起会因为 spring-webmvc 而启动失败）。两者的耦合只是这份字段名, 改动时两边都要看。
 *
 * <h2>为什么只有一个 Path 谓词</h2>
 * 本模块的路由粒度是"服务前缀"（{@code /api/ops/**}）, 一条路由一个谓词就够。
 * 需要更复杂的匹配时再扩展, 现在不为用不到的形态预留结构。
 */
public record GatewayRouteRes(
        String id,
        String uri,
        int order,
        boolean enabled,
        List<Predicate> predicates,
        List<Filter> filters,
        Map<String, Object> metadata) {

    /**
     * 网关谓词。{@code args} 的<b>值必须是字符串</b> —— 网关侧
     * {@code PredicateDefinition.args} 就是 {@code Map<String,String>}。
     */
    public record Predicate(String name, Map<String, String> args) {
    }

    /** 网关过滤器。本模块当前不产生过滤器, 见 {@link #ofPathPrefix}。 */
    public record Filter(String name, Map<String, String> args) {
    }

    /** Path 谓词的工厂名。注意是大小写敏感的精确匹配。 */
    private static final String PATH_PREDICATE = "Path";

    /**
     * 参数名是 {@code patterns}（复数）—— 网关的 {@code PathRoutePredicateFactory.Config}
     * 里那个字段是 {@code List<String>}, 参数名由 {@code shortcutFieldOrder()} 决定。
     * <p>
     * 另外两点是读字节码才发现的, 猜必错:
     * <ul>
     *   <li>该工厂的 {@code shortcutType()} 是 {@code GATHER_LIST_TAIL_FLAG}, 它的 normalize
     *       <b>完全忽略参数名、只遍历 args 的值</b>, 所以这里用 {@code patterns} 还是 yml 简写
     *       产生的 {@code _genkey_0} 是等价的 —— 选 {@code patterns} 只是为了看库/抓包时可读</li>
     *   <li>同一段逻辑会把<b>最后一个值</b>当作末尾布尔标志 {@code matchTrailingSlash},
     *       但仅当它字面上等于 {@code "true"}/{@code "false"}/null。这里只传一个值、
     *       且路径前缀不可能是 {@code "true"}, 所以安全。日后若给这个谓词加第二个参数,
     *       最后一个值会被当作标志吃掉 —— 加参数时务必回来看这段</li>
     * </ul>
     *
     * @param pathPattern 路径前缀, 如 {@code /api/ops/**}
     */
    public static GatewayRouteRes ofPathPrefix(
            String id, String uri, int order, boolean enabled, String pathPattern) {

        return new GatewayRouteRes(
                id,
                uri,
                order,
                enabled,
                List.of(new Predicate(PATH_PREDICATE, Map.of("patterns", pathPattern))),
                // 恒为空: 按路径约定(服务内路径 == 外部路径)不需要 StripPrefix。
                // 真需要过滤器时再加, 现在不为用不到的形态发明一种序列化格式。
                List.of(),
                Map.of());
    }
}
