package com.github.gaohongf.mybatis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/**
 * common 模块的 MyBatis-Plus 自动装配。
 * <p>
 * 之所以走自动装配而不是 @Component, 是因为各服务的启动类在
 * com.github.gaohongf.&lt;服务名&gt; 下, 组件扫描扫不到 common 里的类;
 * 用自动装配可以保证所有引入 common 的模块都自动生效, 无需各自加 @ComponentScan。
 */
@AutoConfiguration
@ConditionalOnClass(MetaObjectHandler.class)
public class MybatisPlusAutoConfiguration {

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new AuditMetaObjectHandler();
    }

    @Bean 
    public PaginationInnerInterceptor paginationInnerInterceptor(){
        return new PaginationInnerInterceptor(DbType.MYSQL);
    }

    @Bean 
    public OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor(){
        return new OptimisticLockerInnerInterceptor();
    }
}
