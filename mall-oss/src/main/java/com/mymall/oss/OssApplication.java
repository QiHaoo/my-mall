package com.mymall.oss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 对象存储服务启动类
 *
 * <p>@MapperScan 已移至 {@code com.mymall.oss.config.MyBatisConfig}，
 * 避免 @WebMvcTest 切片测试时因缺少 DataSource 而启动失败。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class OssApplication {

    public static void main(String[] args) {
        SpringApplication.run(OssApplication.class, args);
    }
}
