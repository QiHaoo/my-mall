package com.mymall.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付信息表
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 *
 * @author mymall
 * @since 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("oms_payment_info")
public class PaymentInfo extends BaseEntity {

    /**
     * 订单号（对外业务号）
     */
    private String orderSn;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 支付宝交易流水号
     */
    private String alipayTradeNo;

    /**
     * 支付总金额
     */
    private BigDecimal totalAmount;

    /**
     * 交易内容
     */
    private String subject;

    /**
     * 支付状态[0-待支付 1-支付中 2-支付成功 3-支付失败 4-已关闭]
     */
    private Integer paymentStatus;

    /**
     * 确认时间
     */
    private LocalDateTime confirmTime;

    /**
     * 回调内容
     */
    private String callbackContent;

    /**
     * 回调时间
     */
    private LocalDateTime callbackTime;
}
