package com.mymall.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * 会员等级
 * </p>
 *
 * @author mymall
 * @since 2026-06-20
 */
@Getter
@Setter
@ToString
@TableName("ums_member_level")
public class MemberLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 等级名称
     */
    private String name;

    /**
     * 等级需要的成长值
     */
    private Integer growthPoint;

    /**
     * 是否为默认等级[0->不是；1->是]
     */
    private Byte defaultStatus;

    /**
     * 免运费标准
     */
    private BigDecimal freeFreightPoint;

    /**
     * 每次评价获取的成长值
     */
    private Integer commentGrowthPoint;

    /**
     * 是否有免邮特权
     */
    private Byte priviledgeFreeFreight;

    /**
     * 是否有会员价格特权
     */
    private Byte priviledgeMemberPrice;

    /**
     * 是否有生日特权
     */
    private Byte priviledgeBirthday;

    /**
     * 备注
     */
    private String note;
}
