package com.mymall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 采购单明细
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 *
 * @author mymall
 * @since 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("wms_purchase_detail")
public class PurchaseDetail extends BaseEntity {

    /**
     * 采购单ID
     */
    private Long purchaseId;

    /**
     * 采购商品SKU ID
     */
    private Long skuId;

    /**
     * 采购数量
     */
    private Integer skuNum;

    /**
     * 采购金额
     */
    private BigDecimal skuPrice;

    /**
     * 仓库ID
     */
    private Long wareId;

    /**
     * 状态[0-新建 1-已分配 2-正在采购 3-已完成 4-采购失败]
     */
    private Integer status;
}
