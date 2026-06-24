package com.mymall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 首页轮播广告
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sms_home_adv")
public class HomeAdv extends BaseEntity {

    /**
     * 名字
     */
    private String name;

    /**
     * 图片地址
     */
    private String pic;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 状态
     */
    private Boolean status;

    /**
     * 点击数
     */
    private Integer clickCount;

    /**
     * 广告详情连接地址
     */
    private String url;

    /**
     * 备注
     */
    private String note;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 发布者
     */
    private Long publisherId;

    /**
     * 审核者
     */
    private Long authId;
}
