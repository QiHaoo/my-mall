package com.mymall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 商品三级分类
 * <p>
 * 继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 * 分类删除采用业务逻辑删除：将 {@code show_status} 置为 0（隐藏），
 * 保留 {@code is_deleted} 字段由 {@code @TableLogic} 统一维护（目前不参与业务删除语义）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("pms_category")
public class Category extends BaseEntity {

    /**
     * 分类名称
     */
    private String name;

    /**
     * 父分类id[0-一级分类]
     */
    private Long parentCid;

    /**
     * 层级[1/2/3]
     */
    private Integer catLevel;

    /**
     * 是否显示[0-不显示 1-显示]
     */
    private Integer showStatus;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 图标地址
     */
    private String icon;

    /**
     * 计量单位
     */
    private String productUnit;

    /**
     * 商品数量
     */
    private Integer productCount;
}
