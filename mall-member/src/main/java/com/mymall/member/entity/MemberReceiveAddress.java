package com.mymall.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * 会员收货地址
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("ums_member_receive_address")
public class MemberReceiveAddress extends BaseEntity {

    /**
     * member_id
     */
    private Long memberId;

    /**
     * 收货人姓名
     */
    private String name;

    /**
     * 电话
     */
    private String phone;

    /**
     * 邮政编码
     */
    private String postCode;

    /**
     * 省份/直辖市
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 区
     */
    private String region;

    /**
     * 详细地址(街道)
     */
    private String detailAddress;

    /**
     * 省市区代码
     */
    private String areacode;

    /**
     * 是否默认
     */
    private Boolean defaultStatus;
}
