package com.mymall.coupon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 优惠券服务启动类
 *
 * <p>@MapperScan 已移至 {@code com.mymall.coupon.config.MyBatisConfig}，
 * 避免 @WebMvcTest 切片测试时加载不必要的 Mapper Bean。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class CouponApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponApplication.class, args);
    }
}
