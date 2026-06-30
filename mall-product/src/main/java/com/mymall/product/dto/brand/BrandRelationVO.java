package com.mymall.product.dto.brand;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 品牌关联分类列表项
 */
@Data
@Schema(description = "品牌关联分类列表项")
public class BrandRelationVO {

    @Schema(description = "关联ID")
    private Long id;

    @Schema(description = "品牌ID")
    private Long brandId;

    @Schema(description = "品牌名（冗余）")
    private String brandName;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名（冗余）")
    private String categoryName;
}
