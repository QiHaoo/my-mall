package com.mymall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券信息
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sms_coupon")
public class Coupon extends BaseEntity {

    /**
     * 优惠卷类型[0->全场赠券；1->会员赠券；2->购物赠券；3->注册赠券]
     */
    private Boolean couponType;

    /**
     * 优惠券图片
     */
    private String couponImg;

    /**
     * 优惠卷名字
     */
    private String couponName;

    /**
     * 数量
     */
    private Integer num;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 每人限领张数
     */
    private Integer perLimit;

    /**
     * 使用门槛
     */
    private BigDecimal minPoint;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 使用类型[0->全场通用；1->指定分类；2->指定商品]
     */
    private Boolean useType;

    /**
     * 备注
     */
    private String note;

    /**
     * 发行数量
     */
    private Integer publishCount;

    /**
     * 已使用数量
     */
    private Integer useCount;

    /**
     * 领取数量
     */
    private Integer receiveCount;

    /**
     * 可以领取的开始日期
     */
    private LocalDateTime enableStartTime;

    /**
     * 可以领取的结束日期
     */
    private LocalDateTime enableEndTime;

    /**
     * 优惠码
     */
    private String code;

    /**
     * 可以领取的会员等级[0->不限等级，其他-对应等级]
     */
    private Boolean memberLevel;

    /**
     * 发布状态[0-未发布，1-已发布]
     */
    private Boolean publish;
}
