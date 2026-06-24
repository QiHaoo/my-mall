package com.mymall.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * 会员收藏的商品
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("ums_member_collect_spu")
public class MemberCollectSpu extends BaseEntity {

    /**
     * 会员id
     */
    private Long memberId;

    /**
     * spu_id
     */
    private Long spuId;

    /**
     * spu_name
     */
    private String spuName;

    /**
     * spu_img
     */
    private String spuImg;
}
