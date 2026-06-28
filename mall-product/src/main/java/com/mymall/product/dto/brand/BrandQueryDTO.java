package com.mymall.product.dto.brand;

import com.mymall.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品牌分页查询条件（Query 参数绑定，非 JSON Body）
 *
 * <p>继承 {@link PageQuery} 统一分页参数，由 {@link com.mymall.product.controller.BrandController#list} 接收。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "品牌分页查询条件")
public class BrandQueryDTO extends PageQuery {

    @Schema(description = "品牌名模糊匹配")
    private String name;

    @Pattern(regexp = "^[A-Za-z]$", message = "首字母必须为单个字母")
    @Schema(description = "首字母精确匹配（后端转大写）", example = "X")
    private String firstLetter;

    @Schema(description = "显示状态：0-隐藏 1-显示", example = "1")
    private Integer showStatus;
}
