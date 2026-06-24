package com.mymall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 秒杀商品通知订阅
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sms_seckill_sku_notice")
public class SeckillSkuNotice extends BaseEntity {

    /**
     * member_id
     */
    private Long memberId;

    /**
     * sku_id
     */
    private Long skuId;

    /**
     * 活动场次id
     */
    private Long sessionId;

    /**
     * 订阅时间
     */
    private LocalDateTime subcribeTime;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 通知方式[0-短信，1-邮件]
     */
    private Boolean noticeType;
}
