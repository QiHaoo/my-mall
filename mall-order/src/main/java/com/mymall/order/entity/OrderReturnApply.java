package com.mymall.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单退货申请
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 *
 * @author mymall
 * @since 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("oms_order_return_apply")
public class OrderReturnApply extends BaseEntity {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 退货商品ID
     */
    private Long skuId;

    /**
     * 订单编号
     */
    private String orderSn;

    /**
     * 会员用户名
     */
    private String memberUsername;

    /**
     * 退款金额
     */
    private BigDecimal returnAmount;

    /**
     * 退货人姓名
     */
    private String returnName;

    /**
     * 退货人电话
     */
    private String returnPhone;

    /**
     * 申请状态[0-待处理 1-退货中 2-已完成 3-已拒绝]
     */
    private Integer status;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    /**
     * 商品图片
     */
    private String skuImg;

    /**
     * 商品名称
     */
    private String skuName;

    /**
     * 商品品牌
     */
    private String skuBrand;

    /**
     * 商品销售属性(JSON)
     */
    private String skuAttrsVals;

    /**
     * 退货数量
     */
    private Integer skuCount;

    /**
     * 商品单价
     */
    private BigDecimal skuPrice;

    /**
     * 商品实际支付单价
     */
    private BigDecimal skuRealPrice;

    /**
     * 退货原因
     */
    private String reason;

    /**
     * 描述
     */
    private String description;

    /**
     * 凭证图片，以逗号隔开
     */
    private String descPics;

    /**
     * 处理备注
     */
    private String handleNote;

    /**
     * 处理人员
     */
    private String handleMan;

    /**
     * 收货人
     */
    private String receiveMan;

    /**
     * 收货时间
     */
    private LocalDateTime receiveTime;

    /**
     * 收货备注
     */
    private String receiveNote;

    /**
     * 收货电话
     */
    private String receivePhone;

    /**
     * 公司收货地址
     */
    private String companyAddress;
}
