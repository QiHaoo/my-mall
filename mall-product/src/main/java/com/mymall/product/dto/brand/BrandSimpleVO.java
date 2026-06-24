package com.mymall.product.dto.brand;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分类下品牌 VO（前台检索精简返回，不含介绍等大字段）
 */
@Data
@Schema(description = "分类下品牌（前台精简）")
public class BrandSimpleVO {

    @Schema(description = "品牌ID")
    private Long id;

    @Schema(description = "品牌名")
    private String name;

    @Schema(description = "品牌logo URL")
    private String logo;

    @Schema(description = "检索首字母")
    private String firstLetter;
}
