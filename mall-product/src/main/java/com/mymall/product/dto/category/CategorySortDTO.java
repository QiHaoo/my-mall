package com.mymall.product.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 拖拽排序 DTO
 */
@Data
@Schema(description = "拖拽排序请求")
public class CategorySortDTO {

    @NotEmpty(message = "排序列表不能为空")
    @Size(max = 100, message = "单次最多排序 100 个分类")
    @Valid
    @Schema(description = "排序项列表")
    private List<SortItem> categories;

    @Data
    @Schema(description = "排序项")
    public static class SortItem {

        @NotNull(message = "分类ID不能为空")
        @Schema(description = "分类ID", example = "5")
        private Long id;

        @NotNull(message = "父分类ID不能为空")
        @Schema(description = "新的父分类ID", example = "2")
        private Long parentCid;

        @NotNull(message = "层级不能为空")
        @Min(value = 1, message = "层级最小为 1")
        @Max(value = 3, message = "层级最大为 3")
        @Schema(description = "新的层级", example = "2")
        private Integer catLevel;

        @NotNull(message = "排序值不能为空")
        @Min(value = 0, message = "排序值不能小于 0")
        @Schema(description = "新的排序值", example = "1")
        private Integer sort;
    }
}
