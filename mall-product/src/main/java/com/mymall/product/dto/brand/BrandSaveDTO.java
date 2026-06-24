package com.mymall.product.dto.brand;

import com.mymall.common.validation.Create;
import com.mymall.common.validation.Update;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 品牌保存 DTO（新增 + 修改共用，用校验分组 Create/Update 区分）
 *
 * <p>新增用 {@code @Validated(Create.class)}，修改用 {@code @Validated(Update.class)}（要求 id/version 必填）。
 * <p>校验注解显式声明 groups：仅属于当前激活分组的约束才会生效（@Validated 指定分组后不会校验 Default 分组）。
 */
@Data
@Schema(description = "新增/修改品牌请求")
public class BrandSaveDTO {

    @NotNull(groups = Update.class, message = "品牌ID不能为空")
    @Schema(description = "品牌ID（修改时必填）", example = "1")
    private Long id;

    @NotBlank(groups = {Create.class, Update.class}, message = "品牌名不能为空")
    @Size(max = 64, groups = {Create.class, Update.class}, message = "品牌名最长 64 字符")
    @Schema(description = "品牌名", example = "小米")
    private String name;

    @NotBlank(groups = {Create.class, Update.class}, message = "logo地址不能为空")
    @Size(max = 1024, groups = {Create.class, Update.class}, message = "logo地址最长 1024 字符")
    @Schema(description = "品牌logo URL（前端直传 OSS 后返回）")
    private String logo;

    @Size(max = 500, groups = {Create.class, Update.class}, message = "品牌介绍最长 500 字符")
    @Schema(description = "品牌介绍")
    private String descript;

    @Schema(description = "显示状态：0-隐藏 1-显示（新增不传默认 1）", example = "1")
    private Integer showStatus;

    @Pattern(regexp = "^[A-Za-z]$", groups = {Create.class, Update.class}, message = "首字母必须为单个字母")
    @Schema(description = "检索首字母（后端统一转大写存储）", example = "X")
    private String firstLetter;

    @Min(value = 0, groups = {Create.class, Update.class}, message = "排序值不能小于 0")
    @Schema(description = "排序值，越小越靠前", example = "0")
    private Integer sort;

    @Schema(description = "关联三级分类ID列表（修改时传 null 表示不变，传空数组表示清空）")
    private List<Long> categoryIds;

    @NotNull(groups = Update.class, message = "版本号不能为空")
    @Schema(description = "乐观锁版本号（修改时必填）", example = "0")
    private Integer version;
}
