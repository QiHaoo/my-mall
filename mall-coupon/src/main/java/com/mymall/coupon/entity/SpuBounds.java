package com.mymall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品spu积分设置
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sms_spu_bounds")
public class SpuBounds extends BaseEntity {

    private Long spuId;

    /**
     * 成长积分
     */
    private BigDecimal growBounds;

    /**
     * 购物积分
     */
    private BigDecimal buyBounds;

    /**
     * 优惠生效情况[1111（四个状态位，从右到左）;0 - 无优惠，成长积分是否赠送;1 - 无优惠，购物积分是否赠送;2 - 有优惠，成长积分是否赠送;3 - 有优惠，购物积分是否赠送【状态位0：不赠送，1：赠送】]
     */
    private Boolean work;
}
