package com.mymall.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 会员服务启动类
 *
 * <p>@EnableFeignClients 启用 Feign 声明式远程调用，自动扫描并创建
 * {@code com.mymall.member.feign} 包下所有 @FeignClient 接口的代理实现。
 * <p>@MapperScan 已移至 {@code com.mymall.member.config.MyBatisConfig}，
 * 避免 @WebMvcTest 切片测试时加载不必要的 Mapper Bean。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class MemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemberApplication.class, args);
    }
}
