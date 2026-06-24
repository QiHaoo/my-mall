package com.mymall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 库存工作单明细
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 *
 * @author mymall
 * @since 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("wms_ware_order_task_detail")
public class WareOrderTaskDetail extends BaseEntity {

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * SKU名称
     */
    private String skuName;

    /**
     * 购买数量
     */
    private Integer skuNum;

    /**
     * 工作单ID
     */
    private Long taskId;
}
