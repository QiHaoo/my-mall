package com.mymall.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 退款信息
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 *
 * @author mymall
 * @since 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("oms_refund_info")
public class RefundInfo extends BaseEntity {

    /**
     * 退货申请ID
     */
    private Long orderReturnId;

    /**
     * 退款金额
     */
    private BigDecimal refund;

    /**
     * 退款交易流水号
     */
    private String refundSn;

    /**
     * 退款状态[0-待退款 1-退款中 2-退款成功 3-退款失败]
     */
    private Integer refundStatus;

    /**
     * 退款渠道[1-支付宝 2-微信 3-银联 4-汇款]
     */
    private Integer refundChannel;

    /**
     * 退款回调内容
     */
    private String refundContent;
}
