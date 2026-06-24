package com.mymall.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * 会员收藏的专题活动
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("ums_member_collect_subject")
public class MemberCollectSubject extends BaseEntity {

    /**
     * subject_id
     */
    private Long subjectId;

    /**
     * subject_name
     */
    private String subjectName;

    /**
     * subject_img
     */
    private String subjectImg;

    /**
     * 活动url
     */
    private String subjectUrll;
}
