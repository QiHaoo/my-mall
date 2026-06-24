package com.mymall.product.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改分类 DTO
 */
@Data
@Schema(description = "修改分类请求")
public class CategoryUpdateDTO {

    @NotNull(message = "分类ID不能为空")
    @Schema(description = "分类ID", example = "5")
    private Long id;

    @Size(max = 50, message = "分类名称最长 50 字符")
    @Schema(description = "分类名称")
    private String name;

    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "排序值")
    private Integer sort;

    @Size(max = 255, message = "图标地址最长 255 字符")
    @Schema(description = "图标地址")
    private String icon;

    @Size(max = 50, message = "计量单位最长 50 字符")
    @Schema(description = "计量单位")
    private String productUnit;
}
