package com.mymall.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * 优惠券领取历史记录
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Getter
@Setter
@ToString
@TableName("sms_coupon_history")
public class CouponHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 优惠券id
     */
    private Long couponId;

    /**
     * 会员id
     */
    private Long memberId;

    /**
     * 会员名字
     */
    private String memberNickName;

    /**
     * 获取方式[0->后台赠送；1->主动领取]
     */
    private Boolean getType;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 使用状态[0->未使用；1->已使用；2->已过期]
     */
    private Boolean useType;

    /**
     * 使用时间
     */
    private LocalDateTime useTime;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 订单号
     */
    private Long orderSn;
}
