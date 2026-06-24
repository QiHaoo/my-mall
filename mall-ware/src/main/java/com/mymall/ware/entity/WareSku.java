package com.mymall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 商品库存
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 *
 * @author mymall
 * @since 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("wms_ware_sku")
public class WareSku extends BaseEntity {

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 仓库ID
     */
    private Long wareId;

    /**
     * 库存数
     */
    private Integer stock;

    /**
     * SKU名称
     */
    private String skuName;

    /**
     * 锁定库存
     */
    private Integer stockLocked;
}
