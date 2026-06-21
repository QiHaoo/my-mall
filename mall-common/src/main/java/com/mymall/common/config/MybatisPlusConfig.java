package com.mymall.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置
 * <p>
 * 分页插件：PaginationInnerInterceptor（MyBatis-Plus 分页必须配置）
 * 乐观锁插件：OptimisticLockerInnerInterceptor（配合 @Version 注解使用）
 * <p>
 * 通过 @ComponentScan 确保 common 包下所有 @Component
 * （如 MyMetaObjectHandler、GlobalExceptionHandler）都能被业务模块扫描到。
 */
@Configuration
@ComponentScan("com.mymall.common")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
