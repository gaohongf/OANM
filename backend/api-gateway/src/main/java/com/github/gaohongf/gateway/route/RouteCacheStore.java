package com.github.gaohongf.gateway.route;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 路由定义的本地缓存（last-known-good）。
 *
 * <h2>解决什么问题</h2>
 * 路由的唯一权威来源是 service-auth 的数据库。但网关启动时如果它正好不可用, 网关就会
 * <b>一条路由都没有</b> —— 所有请求 404, 整个系统对外不可用。
 * <p>
 * 用一份本地文件记住上次成功拉取的路由, 就能把这种情况降级成"用稍旧的路由继续服务"。
 * 对管理后台而言, 这个取舍明显更好: 路由晚更新几分钟不致命, 全站 404 才致命。
 *
 * <h2>写文件是"原子替换"而不是直接覆盖</h2>
 * 先写临时文件再 move。直接往目标文件写的话, 如果写到一半进程被 kill（或磁盘满）,
 * 留下的就是一份<b>截断的、无法解析的</b>缓存 —— 下次启动读它会失败, 兜底等于不存在。
 * 而这个文件恰恰是在"环境已经出问题"的时候才被使用, 所以它自己是不能出问题的。
 */
@Slf4j
@Component
public class RouteCacheStore {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path cacheFile;

    public RouteCacheStore(GatewayRouteProperties properties) {
        this.cacheFile = Path.of(properties.getCacheFile()).toAbsolutePath();
    }

    /** 落盘。失败只记日志 —— 缓存写不进去不该让这次刷新整体失败。 */
    public void save(List<RouteDefinition> routes) {
        Path temp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        try {
            // RouteDefinition 没有 Jackson 注解, 但它是普通 POJO, 按字段序列化即可。
            // URI 字段需要 Jackson 的 JdkModule? 不需要 —— java.net.URI 是 Jackson 内置支持的类型。
            byte[] json = objectMapper.writeValueAsBytes(routes);
            Files.write(temp, json);
            Files.move(temp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            log.debug("已缓存 {} 条路由到 {}", routes.size(), cacheFile);
        } catch (IOException failure) {
            log.warn("路由缓存写入失败({}), 下次 service-auth 不可用启动时将没有兜底路由", cacheFile, failure);
        }
    }

    /**
     * 读取缓存。
     *
     * @return 缓存中的路由; 文件不存在或解析失败时返回空列表（并记日志）
     */
    public List<RouteDefinition> load() {
        if (!Files.exists(cacheFile)) {
            log.info("路由缓存文件 {} 不存在, 本次启动没有兜底路由可用", cacheFile);
            return List.of();
        }
        try {
            RouteDefinition[] routes = objectMapper.readValue(Files.readAllBytes(cacheFile), RouteDefinition[].class);
            log.info("已从缓存 {} 载入 {} 条路由", cacheFile, routes.length);
            return Arrays.asList(routes);
        } catch (IOException failure) {
            // 解析失败说明文件坏了（例如上一版程序写入的格式不同）。
            // 返回空而不是抛异常: 调用方还有"从 service-auth 拉取"这条路, 不该被一个坏缓存挡住。
            log.warn("路由缓存 {} 解析失败, 将忽略它并尝试从 service-auth 拉取", cacheFile, failure);
            return List.of();
        }
    }
}
