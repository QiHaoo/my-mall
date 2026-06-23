package com.mymall.oss.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置
 * <p>
 * @MapperScan 必须独立为配置类，不放在启动类上，
 * 避免 @WebMvcTest 切片测试时因缺少 DataSource 导致 ApplicationContext 加载失败。
 */
@Configuration
@MapperScan("com.mymall.oss.mapper")
public class MyBatisConfig {

}
