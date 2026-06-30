package com.mymall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品牌分类关联
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_category_brand_relation")
public class CategoryBrandRelation extends BaseEntity {

    /**
     * 品牌id
     */
    private Long brandId;

    /**
     * 分类id
     */
    private Long categoryId;

    /**
     * 品牌名（冗余）
     */
    private String brandName;

    /**
     * 分类名（冗余）
     */
    private String categoryName;
}
