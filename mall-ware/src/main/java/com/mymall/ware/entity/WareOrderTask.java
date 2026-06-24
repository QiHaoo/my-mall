package com.mymall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 库存工作单
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 *
 * @author mymall
 * @since 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("wms_ware_order_task")
public class WareOrderTask extends BaseEntity {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 收货人
     */
    private String consignee;

    /**
     * 收货人电话
     */
    private String consigneeTel;

    /**
     * 配送地址
     */
    private String deliveryAddress;

    /**
     * 订单备注
     */
    private String orderComment;

    /**
     * 付款方式[1-在线付款 2-货到付款]
     */
    private Integer paymentWay;

    /**
     * 任务状态[0-待处理 1-已分配 2-正在出库 3-已发货 4-已完成 5-已取消]
     */
    private Integer taskStatus;

    /**
     * 订单描述
     */
    private String orderBody;

    /**
     * 物流单号
     */
    private String trackingNo;

    /**
     * 仓库ID
     */
    private Long wareId;

    /**
     * 工作单备注
     */
    private String taskComment;
}
