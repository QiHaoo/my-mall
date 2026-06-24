package com.mymall.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置
 *
 * <p>通过 @ComponentScan("com.mymall.common") 确保 common 包下所有 @Component
 * （如 MyMetaObjectHandler）都能被业务模块扫描到。该扫描限定在 common 包内，影响可控。
 *
 * <p>拦截器：
 * <ul>
 *   <li>PaginationInnerInterceptor：分页，maxLimit=500 兜底防止 pageSize 爆破（与 PageQuery 上限一致）</li>
 *   <li>OptimisticLockerInnerInterceptor：乐观锁，配合 BaseEntity 的 @Version 字段</li>
 * </ul>
 *
 * <p>全局配置（GlobalConfig）：显式声明主键策略与逻辑删除字段，生产级不依赖隐式默认。
 * BaseEntity 已用注解声明 @TableId(ASSIGN_ID)/@TableLogic，此处为全局兜底。
 */
@Configuration
@ComponentScan("com.mymall.common")
public class MybatisPlusConfig {

    /** 分页单页上限兜底，防止恶意传超大 pageSize 拖垮 DB */
    private static final long MAX_PAGE_LIMIT = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_LIMIT);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public GlobalConfig globalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setIdType(IdType.ASSIGN_ID);
        dbConfig.setLogicDeleteField("isDeleted");
        dbConfig.setLogicDeleteValue("1");
        dbConfig.setLogicNotDeleteValue("0");
        globalConfig.setDbConfig(dbConfig);
        return globalConfig;
    }
}
