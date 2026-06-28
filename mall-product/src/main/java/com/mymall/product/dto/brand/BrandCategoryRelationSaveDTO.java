package com.mymall.product.dto.brand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增品牌-分类关联请求
 */
@Data
@Schema(description = "新增品牌-分类关联")
public class BrandCategoryRelationSaveDTO {

    @NotNull(message = "品牌ID不能为空")
    @Schema(description = "品牌ID")
    private Long brandId;

    @NotNull(message = "分类ID不能为空")
    @Schema(description = "三级分类ID")
    private Long catelogId;
}
