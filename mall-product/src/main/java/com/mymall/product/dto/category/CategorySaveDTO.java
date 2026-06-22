package com.mymall.product.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增分类 DTO
 */
@Data
@Schema(description = "新增分类请求")
public class CategorySaveDTO {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最长 50 字符")
    @Schema(description = "分类名称", example = "手机通讯")
    private String name;

    @NotNull(message = "父分类ID不能为空")
    @Schema(description = "父分类ID，一级分类传 0", example = "1")
    private Long parentCid;

    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "排序值，越小越靠前", example = "5")
    private Integer sort;

    @Size(max = 255, message = "图标地址最长 255 字符")
    @Schema(description = "图标地址")
    private String icon;

    @Size(max = 50, message = "计量单位最长 50 字符")
    @Schema(description = "计量单位", example = "台")
    private String productUnit;
}
