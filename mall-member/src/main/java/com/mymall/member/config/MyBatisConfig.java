package com.mymall.member.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置 — 独立于启动类，方便 @WebMvcTest 等切片测试时排除
 */
@Configuration
@MapperScan("com.mymall.member.mapper")
public class MyBatisConfig {
}
