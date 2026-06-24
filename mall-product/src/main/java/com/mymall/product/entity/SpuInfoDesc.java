package com.mymall.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * spu信息介绍（1:1 扩展表）
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 * <p>新表主键为 id（雪花算法），spu_id 降级为唯一业务列（uk_spu_id），不再是主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_spu_info_desc")
public class SpuInfoDesc extends BaseEntity {

    /**
     * 商品id（唯一业务列，uk_spu_id）
     */
    @TableField("spu_id")
    private Long spuId;

    /**
     * 商品介绍
     */
    private String decript;
}
