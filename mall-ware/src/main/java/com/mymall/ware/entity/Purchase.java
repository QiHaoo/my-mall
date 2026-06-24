package com.mymall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 采购信息
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 *
 * @author mymall
 * @since 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("wms_purchase")
public class Purchase extends BaseEntity {

    /**
     * 采购人用户ID
     */
    private Long assigneeId;

    /**
     * 采购人姓名
     */
    private String assigneeName;

    /**
     * 采购人联系方式
     */
    private String phone;

    /**
     * 优先级[0-低 1-中 2-高]
     */
    private Integer priority;

    /**
     * 状态[0-新建 1-已分配 2-正在采购 3-已完成 4-采购失败]
     */
    private Integer status;

    /**
     * 仓库ID
     */
    private Long wareId;

    /**
     * 采购总金额
     */
    private BigDecimal amount;
}
