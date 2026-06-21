package com.mymall.common.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询基类
 * <p>
 * 所有分页查询的 DTO 应继承此类，统一分页参数命名和校验。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class CouponPageQuery extends PageQuery {
 *     private String couponName;    // 优惠券名称（模糊查询）
 *     private Integer couponType;   // 优惠券类型
 * }
 *
 * // Controller
 * @GetMapping("/page")
 * public R<IPage<Coupon>> page(@Valid CouponPageQuery query) {
 *     return R.ok(couponService.page(query));
 * }
 * }</pre>
 */
@Data
@Schema(description = "分页查询参数")
public class PageQuery {

    @Schema(description = "页码（从 1 开始）", defaultValue = "1", example = "1")
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    @Schema(description = "每页数量", defaultValue = "10", example = "10")
    @Min(value = 1, message = "每页数量最小为 1")
    @Max(value = 500, message = "每页数量最大为 500")
    private Integer pageSize = 10;
}
