package com.github.gaohongf.software.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.gaohongf.software.rsm.SoftwareRsm;

/**
 * 注册本服务的 RSM 消息类。
 *
 * <h2>为什么用 @Bean 而不是给 {@link SoftwareRsm} 加 @Component</h2>
 * 消息类以后很可能被挪进 common, 那时它的包不再被任何服务的扫描覆盖, 注册会静默失效。
 * 显式声明后, 类放在哪儿都不影响注册。
 */
@Configuration
public class SoftwareRsmConfiguration {

    @Bean
    public SoftwareRsm softwareRsm() {
        return new SoftwareRsm();
    }
}
