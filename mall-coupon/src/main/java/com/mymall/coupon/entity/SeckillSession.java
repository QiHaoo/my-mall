package com.mymall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 秒杀活动场次
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sms_seckill_session")
public class SeckillSession extends BaseEntity {

    /**
     * 场次名称
     */
    private String name;

    /**
     * 每日开始时间
     */
    private LocalDateTime startTime;

    /**
     * 每日结束时间
     */
    private LocalDateTime endTime;

    /**
     * 启用状态
     */
    private Boolean status;
}
