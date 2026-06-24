package com.mymall.product.dto.brand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新品牌显示状态 DTO
 */
@Data
@Schema(description = "更新品牌显示状态请求")
public class BrandShowStatusDTO {

    @NotNull(message = "显示状态不能为空")
    @Min(value = 0, message = "显示状态只能为 0 或 1")
    @Max(value = 1, message = "显示状态只能为 0 或 1")
    @Schema(description = "显示状态：0-隐藏 1-显示", example = "1")
    private Integer showStatus;
}
