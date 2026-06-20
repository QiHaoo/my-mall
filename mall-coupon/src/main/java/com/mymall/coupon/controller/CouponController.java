package com.mymall.coupon.controller;

import com.mymall.common.result.R;
import com.mymall.coupon.entity.Coupon;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券服务示例接口 — 供其他服务通过 Feign 远程调用
 */
@RestController
@RequestMapping("/coupon/coupon")
public class CouponController {

    /**
     * 示例远程调用接口：返回两个优惠券实体列表
     * <p>实际项目实现时，数据来源于 Service → Mapper → DB 查询链路。
     */
    @GetMapping("/list")
    public R list() {
        // 模拟两条优惠券数据，演示远程调用时实体对象的序列化传输
        Coupon c1 = new Coupon();
        c1.setId(1L);
        c1.setCouponName("满100减20");
        c1.setAmount(new BigDecimal("20.00"));
        c1.setMinPoint(new BigDecimal("100.00"));
        c1.setStartTime(LocalDateTime.now());
        c1.setEndTime(LocalDateTime.now().plusDays(7));

        Coupon c2 = new Coupon();
        c2.setId(2L);
        c2.setCouponName("新人专享8折券");
        c2.setAmount(new BigDecimal("0.80"));
        c2.setMinPoint(new BigDecimal("0.00"));
        c2.setStartTime(LocalDateTime.now());
        c2.setEndTime(LocalDateTime.now().plusDays(30));

        List<Coupon> coupons = List.of(c1, c2);
        return R.ok().put("coupons", coupons).put("total", coupons.size());
    }
}
