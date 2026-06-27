package com.mymall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mymall.common.result.R;
import com.mymall.common.validation.Create;
import com.mymall.common.validation.Update;
import com.mymall.product.dto.brand.BrandQueryDTO;
import com.mymall.product.dto.brand.BrandSaveDTO;
import com.mymall.product.dto.brand.BrandShowStatusDTO;
import com.mymall.product.dto.brand.BrandSimpleVO;
import com.mymall.product.dto.brand.BrandVO;
import com.mymall.product.service.IBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 品牌管理接口
 *
 * <p>只做参数校验和结果封装，业务逻辑全在 Service 层。
 */
@Slf4j
@RestController
@RequestMapping("/product/brand")
@RequiredArgsConstructor
@Tag(name = "品牌管理")
public class BrandController {

    private final IBrandService brandService;

    @Operation(summary = "分页查询品牌")
    @GetMapping
    public R<Page<BrandVO>> list(BrandQueryDTO query) {
        return R.ok(brandService.pageQuery(query));
    }

    @Operation(summary = "品牌详情")
    @GetMapping("/{id}")
    public R<BrandVO> getById(@PathVariable Long id) {
        return R.ok(brandService.getBrandDetail(id));
    }

    @Operation(summary = "新增品牌")
    @PostMapping
    public R<Void> save(@Validated(Create.class) @RequestBody BrandSaveDTO dto) {
        log.info("新增品牌: name={}", dto.getName());
        brandService.saveBrand(dto);
        return R.ok(null);
    }

    @Operation(summary = "修改品牌")
    @PutMapping
    public R<Void> update(@Validated(Update.class) @RequestBody BrandSaveDTO dto) {
        log.info("修改品牌: id={}", dto.getId());
        brandService.updateBrand(dto);
        return R.ok(null);
    }

    @Operation(summary = "更新品牌显示状态")
    @PutMapping("/{id}/show-status")
    public R<Void> updateShowStatus(@PathVariable Long id,
                                    @Valid @RequestBody BrandShowStatusDTO dto) {
        log.info("更新品牌显示状态: id={}, showStatus={}", id, dto.getShowStatus());
        brandService.updateShowStatus(id, dto);
        return R.ok();
    }

    @Operation(summary = "删除品牌")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        log.info("删除品牌: id={}", id);
        brandService.removeBrand(id);
        return R.ok();
    }

    @Operation(summary = "查询分类下的品牌")
    @GetMapping("/by-category/{catelogId}")
    public R<List<BrandSimpleVO>> listByCategory(@PathVariable Long catelogId) {
        return R.ok(brandService.listByCategory(catelogId));
    }
}
