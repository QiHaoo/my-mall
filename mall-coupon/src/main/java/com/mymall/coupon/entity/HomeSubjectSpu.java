package com.mymall.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * 专题商品
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Getter
@Setter
@ToString
@TableName("sms_home_subject_spu")
public class HomeSubjectSpu implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 专题名字
     */
    private String name;

    /**
     * 专题id
     */
    private Long subjectId;

    /**
     * spu_id
     */
    private Long spuId;

    /**
     * 排序
     */
    private Integer sort;
}
