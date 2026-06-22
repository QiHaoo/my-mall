package com.mymall.product.controller;

import com.mymall.common.result.R;
import com.mymall.product.dto.category.*;
import com.mymall.product.service.ICategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品分类管理接口
 *
 * <p>只做参数校验和结果封装，业务逻辑全在 Service 层。
 */
@Slf4j
@RestController
@RequestMapping("/product/category")
@RequiredArgsConstructor
@Tag(name = "商品分类管理")
public class CategoryController {

    private final ICategoryService categoryService;

    @Operation(summary = "查询分类树")
    @GetMapping("/tree")
    public R<List<CategoryVO>> tree() {
        return R.ok(categoryService.listTree());
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public R<Void> save(@Validated @RequestBody CategorySaveDTO dto) {
        log.info("新增分类: name={}, parentCid={}", dto.getName(), dto.getParentCid());
        categoryService.saveCategory(dto);
        return R.ok(null);
    }

    @Operation(summary = "修改分类")
    @PutMapping
    public R<Void> update(@Validated @RequestBody CategoryUpdateDTO dto) {
        log.info("修改分类: catId={}", dto.getCatId());
        categoryService.updateCategory(dto);
        return R.ok(null);
    }

    @Operation(summary = "批量删除分类")
    @PostMapping("/batch-delete")
    public R<Void> batchDelete(@Validated @RequestBody CategoryBatchDeleteDTO dto) {
        log.info("批量删除分类: ids={}", dto.getIds());
        categoryService.batchDelete(dto.getIds());
        return R.ok(null);
    }

    @Operation(summary = "拖拽排序")
    @PutMapping("/sort")
    public R<Void> sort(@Validated @RequestBody CategorySortDTO dto) {
        log.info("拖拽排序: count={}", dto.getCategories().size());
        categoryService.sortCategories(dto);
        return R.ok(null);
    }
}
