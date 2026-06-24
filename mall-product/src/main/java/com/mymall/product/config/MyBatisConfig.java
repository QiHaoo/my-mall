package com.mymall.product.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置 — 独立于启动类，方便 @WebMvcTest 等切片测试时排除。
 *
 * <p>若将 @MapperScan 写在启动类上，@WebMvcTest 切片测试会尝试初始化 Mapper Bean，
 * 因缺少 DataSource 导致 ApplicationContext 加载失败，故按规范独立至此。
 */
@Configuration
@MapperScan("com.mymall.product.mapper")
public class MyBatisConfig {
}
