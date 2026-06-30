package com.mymall.product.service;

import com.mymall.product.dto.brand.BrandCategoryRelationSaveDTO;
import com.mymall.product.dto.brand.BrandRelationVO;
import com.mymall.product.entity.CategoryBrandRelation;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 品牌分类关联服务接口
 */
public interface ICategoryBrandRelationService extends IService<CategoryBrandRelation> {

    /**
     * 查询品牌已关联的三级分类列表
     *
     * @param brandId 品牌ID
     * @return 关联分类列表
     */
    List<BrandRelationVO> listByBrandId(Long brandId);

    /**
     * 新增品牌-分类关联
     *
     * @param dto 关联参数
     */
    void saveRelation(BrandCategoryRelationSaveDTO dto);

    /**
     * 移除品牌-分类关联（逻辑删除）
     *
     * @param brandId    品牌ID
     * @param categoryId 分类ID
     */
    void removeRelation(Long brandId, Long categoryId);

    /**
     * 品牌改名时同步刷新关联表冗余品牌名
     *
     * @param brandId 品牌ID
     * @param newName 新品牌名
     */
    void updateBrandName(Long brandId, String newName);

    /**
     * 分类改名时同步刷新关联表冗余分类名
     *
     * @param categoryId    分类ID
     * @param newCategoryName 新分类名
     */
    void updateCategoryName(Long categoryId, String newCategoryName);
}
