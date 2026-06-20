package com.mymall.member.controller;

import com.mymall.common.result.R;
import com.mymall.member.feign.CouponFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员服务示例接口 — 演示通过 Feign 远程调用 mall-coupon
 */
@RestController
@RequestMapping("/member/member")
@RequiredArgsConstructor
public class MemberController {

    private final CouponFeignClient couponFeignClient;

    /**
     * 调用远程 coupon 服务获取优惠券列表，验证服务间调用链路
     */
    @GetMapping("/test-remote")
    public R testRemote() {
        // Feign 自动将 coupon 服务返回的 R 反序列化为 R 对象
        return couponFeignClient.list();
    }
}
