package com.mymall.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * 成长值变化历史记录
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("ums_growth_change_history")
public class GrowthChangeHistory extends BaseEntity {

    /**
     * member_id
     */
    private Long memberId;

    /**
     * 改变的值（正负计数）
     */
    private Integer changeCount;

    /**
     * 备注
     */
    private String note;

    /**
     * 积分来源[0-购物，1-管理员修改]
     */
    private Byte sourceType;
}
