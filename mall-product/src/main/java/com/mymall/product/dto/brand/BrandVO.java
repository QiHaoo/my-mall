package com.mymall.product.dto.brand;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 品牌详情/列表项 VO
 *
 * <p>详情接口额外返回 {@link #categoryIds}（关联的三级分类 ID 列表）；列表接口该字段为 null。
 */
@Data
@Schema(description = "品牌详情/列表项")
public class BrandVO {

    @Schema(description = "品牌ID")
    private Long id;

    @Schema(description = "品牌名")
    private String name;

    @Schema(description = "品牌logo URL")
    private String logo;

    @Schema(description = "品牌介绍")
    private String descript;

    @Schema(description = "显示状态：0-隐藏 1-显示")
    private Integer showStatus;

    @Schema(description = "检索首字母")
    private String firstLetter;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "关联三级分类ID列表（仅详情接口返回）")
    private List<Long> categoryIds;
}
