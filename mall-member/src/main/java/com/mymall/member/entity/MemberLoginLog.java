package com.mymall.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * 会员登录记录
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("ums_member_login_log")
public class MemberLoginLog extends BaseEntity {

    /**
     * member_id
     */
    private Long memberId;

    /**
     * ip
     */
    private String ip;

    /**
     * city
     */
    private String city;

    /**
     * 登录类型[1-web，2-app]
     */
    private Boolean loginType;
}
