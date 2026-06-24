package com.mymall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * spu图片
 *
 * <p>继承 {@link BaseEntity} 复用 id / 审计字段 / 逻辑删除（is_deleted）/ 乐观锁（version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_spu_images")
public class SpuImages extends BaseEntity {

    /**
     * spu_id
     */
    private Long spuId;

    /**
     * 图片名
     */
    private String imgName;

    /**
     * 图片地址
     */
    private String imgUrl;

    /**
     * 顺序
     */
    private Integer imgSort;

    /**
     * 是否默认图
     */
    private Byte defaultImg;
}
