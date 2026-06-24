package com.mymall.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 订单项信息
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 *
 * @author mymall
 * @since 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("oms_order_item")
public class OrderItem extends BaseEntity {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * SPU ID
     */
    private Long spuId;

    /**
     * SPU 名称
     */
    private String spuName;

    /**
     * SPU 图片
     */
    private String spuPic;

    /**
     * 品牌
     */
    private String spuBrand;

    /**
     * 商品分类ID
     */
    private Long categoryId;

    /**
     * 商品SKU编号
     */
    private Long skuId;

    /**
     * 商品SKU名称
     */
    private String skuName;

    /**
     * 商品SKU图片
     */
    private String skuPic;

    /**
     * 商品SKU价格
     */
    private BigDecimal skuPrice;

    /**
     * 商品购买数量
     */
    private Integer skuQuantity;

    /**
     * 商品销售属性组合（JSON）
     */
    private String skuAttrsVals;

    /**
     * 商品促销分解金额
     */
    private BigDecimal promotionAmount;

    /**
     * 优惠券优惠分解金额
     */
    private BigDecimal couponAmount;

    /**
     * 积分优惠分解金额
     */
    private BigDecimal integrationAmount;

    /**
     * 该商品经过优惠后的分解金额
     */
    private BigDecimal realAmount;

    /**
     * 赠送积分
     */
    private Integer giftIntegration;

    /**
     * 赠送成长值
     */
    private Integer giftGrowth;
}
