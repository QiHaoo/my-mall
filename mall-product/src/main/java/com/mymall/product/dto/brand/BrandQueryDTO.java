package com.mymall.product.dto.brand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 品牌分页查询条件（Query 参数绑定，非 JSON Body）
 *
 * <p>分页参数由 {@link com.mymall.product.controller.BrandController#list} 接收，
 * 单页上限由 MyBatis-Plus 分页插件 maxLimit 兜底。
 */
@Data
@Schema(description = "品牌分页查询条件")
public class BrandQueryDTO {

    @Min(value = 1, message = "页码从 1 开始")
    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数至少 1")
    @Max(value = 100, message = "每页最多 100 条")
    @Schema(description = "每页条数，最大 100", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "品牌名模糊匹配")
    private String name;

    @Pattern(regexp = "^[A-Za-z]$", message = "首字母必须为单个字母")
    @Schema(description = "首字母精确匹配（后端转大写）", example = "X")
    private String firstLetter;

    @Schema(description = "显示状态：0-隐藏 1-显示", example = "1")
    private Integer showStatus;
}
