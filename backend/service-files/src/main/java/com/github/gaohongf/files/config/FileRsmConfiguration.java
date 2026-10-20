package com.github.gaohongf.files.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.gaohongf.files.rsm.FileRsm;

/**
 * 注册本服务的 RSM 消息类。
 *
 * <p>
 * 用 {@code @Bean} 而不是给 {@link FileRsm} 加 {@code @Component}: 消息类日后若被挪到
 * {@code common}，它的包就不再被本服务扫描，注册会静默失效。显式声明后放哪儿都不影响。
 */
@Configuration
public class FileRsmConfiguration {

    @Bean
    public FileRsm fileRsm() {
        return new FileRsm();
    }
}
