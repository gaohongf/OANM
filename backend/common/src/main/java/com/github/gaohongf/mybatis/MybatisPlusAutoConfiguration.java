package com.github.gaohongf.mybatis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
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

    /**
     * 把各个 InnerInterceptor 装进 MybatisPlusInterceptor。
     *
     * <h3>为什么不能把 InnerInterceptor 直接声明成 bean</h3>
     * MyBatis 只认实现了 {@code org.apache.ibatis.plugin.Interceptor} 的 bean,
     * 而 {@code InnerInterceptor} <b>不是</b> {@code Interceptor} —— 它必须由
     * {@link MybatisPlusInterceptor}（它才是 Interceptor）持有。
     * <p>
     * 之前这里是两个独立的 {@code @Bean}: 上下文中确实有这两个对象, 启动也不报错,
     * 但它们从未被挂到 MyBatis 上。症状是<b>完全静默</b>的:
     * <ul>
     *   <li>分页失效 —— {@code selectPage} 返回全表, 且 {@code total} 恒为 0
     *       （看起来像"查询成功但总数算错了"）</li>
     *   <li>乐观锁失效 —— {@code BaseEntity.version} 上的 {@code @Version} 形同虚设,
     *       并发更新不会失败也不会重试, 后写覆盖先写</li>
     * </ul>
     * 两者都曾经"配了但没生效", 排查成本很高, 所以这里写清楚: 新增 InnerInterceptor
     * 一律加到下面这个方法里, 不要单独声明成 bean。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
