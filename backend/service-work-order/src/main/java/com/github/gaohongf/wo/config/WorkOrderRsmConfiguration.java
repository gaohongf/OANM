package com.github.gaohongf.wo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.gaohongf.wo.rsm.WorkOrderRsm;

/**
 * 注册本服务的 RSM 消息类。
 *
 * <h2>为什么用 @Bean 而不是给 {@link WorkOrderRsm} 加 @Component</h2>
 * 它的包 {@code com.github.gaohongf.wo.rsm} 在启动类 {@code WorkOrderApplication} 的
 * 扫描范围内，加 {@code @Component} 眼下也能生效。但 {@code AuthRsm} 的教训是：
 * 消息类一旦被挪到 {@code common}（那是最自然的重构方向 —— 别的服务也要用工单消息时就会这么做），
 * 它的包就不再被任何服务的扫描覆盖，注册<b>静默失效</b>，失败被渲染成"成功"。
 * 显式声明 {@code @Bean} 之后，类放在哪儿都不影响注册，重构时不会踩雷。
 */
@Configuration
public class WorkOrderRsmConfiguration {

    @Bean
    public WorkOrderRsm workOrderRsm() {
        return new WorkOrderRsm();
    }
}
