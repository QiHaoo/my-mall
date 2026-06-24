package com.mymall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 秒杀活动商品关联
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sms_seckill_sku_relation")
public class SeckillSkuRelation extends BaseEntity {

    /**
     * 活动id
     */
    private Long promotionId;

    /**
     * 活动场次id
     */
    private Long promotionSessionId;

    /**
     * 商品id
     */
    private Long skuId;

    /**
     * 秒杀价格
     */
    private Long seckillPrice;

    /**
     * 秒杀总量
     */
    private Long seckillCount;

    /**
     * 每人限购数量
     */
    private Long seckillLimit;

    /**
     * 排序
     */
    private Integer seckillSort;
}
