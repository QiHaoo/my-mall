package com.mymall.product.dto.brand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除品牌请求
 */
@Data
@Schema(description = "批量删除品牌")
public class BrandBatchDeleteDTO {

    @NotEmpty(message = "id列表不能为空")
    @Schema(description = "品牌ID列表")
    private List<Long> ids;
}
