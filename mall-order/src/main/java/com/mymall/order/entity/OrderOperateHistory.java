package com.mymall.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 订单操作历史记录
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 *
 * @author mymall
 * @since 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("oms_order_operate_history")
public class OrderOperateHistory extends BaseEntity {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 操作人[用户/系统/后台管理员]
     */
    private String operateMan;

    /**
     * 订单状态[0-待付款 1-待发货 2-已发货 3-已完成 4-已关闭 5-无效订单]
     */
    private Integer orderStatus;

    /**
     * 备注
     */
    private String note;
}
