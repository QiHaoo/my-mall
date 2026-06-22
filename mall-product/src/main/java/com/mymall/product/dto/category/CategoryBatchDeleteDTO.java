package com.mymall.product.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除分类 DTO
 */
@Data
@Schema(description = "批量删除分类请求")
public class CategoryBatchDeleteDTO {

    @NotEmpty(message = "ID列表不能为空")
    @Size(max = 100, message = "单次最多删除 100 个分类")
    @Schema(description = "分类ID列表", example = "[1, 2, 3]")
    private List<Long> ids;
}
