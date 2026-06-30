package com.mymall.product.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 分类树节点 VO
 * <p>
 * 用于分类树查询接口返回，包含 children 字段支持前端树形控件渲染。
 */
@Data
@Schema(description = "分类树节点")
public class CategoryVO {

    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "父分类ID")
    private Long parentId;

    @Schema(description = "层级（1/2/3）")
    private Integer level;

    @Schema(description = "显示状态：0-隐藏，1-显示")
    private Integer showStatus;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "图标地址")
    private String icon;

    @Schema(description = "计量单位")
    private String productUnit;

    @Schema(description = "商品数量")
    private Integer productCount;

    @Schema(description = "子分类列表")
    private List<CategoryVO> children;
}
