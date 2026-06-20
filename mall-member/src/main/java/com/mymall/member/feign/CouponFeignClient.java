package com.mymall.member.feign;

import com.mymall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 远程调用 mall-coupon 服务的 Feign 客户端接口
 *
 * <p>name = 远程服务在 Nacos 中注册的服务名
 * <p>path = 远程服务 Controller 上的 @RequestMapping 路径前缀
 * <p>返回类型 R 会被 Feign 自动反序列化还原为统一响应体。
 */
@FeignClient(name = "mall-coupon", path = "/coupon/coupon")
public interface CouponFeignClient {

    /** 调用 coupon 服务的优惠券列表接口 */
    @GetMapping("/list")
    R list();
}
