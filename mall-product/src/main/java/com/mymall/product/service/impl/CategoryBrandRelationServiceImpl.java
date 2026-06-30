package com.mymall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mymall.common.exception.BizException;
import com.mymall.common.exception.ResultCode;
import com.mymall.product.dto.brand.BrandCategoryRelationSaveDTO;
import com.mymall.product.dto.brand.BrandRelationVO;
import com.mymall.product.entity.Brand;
import com.mymall.product.entity.Category;
import com.mymall.product.entity.CategoryBrandRelation;
import com.mymall.product.mapper.BrandMapper;
import com.mymall.product.mapper.CategoryBrandRelationMapper;
import com.mymall.product.mapper.CategoryMapper;
import com.mymall.product.service.ICategoryBrandRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 品牌分类关联服务实现
 */
@Service
@RequiredArgsConstructor
public class CategoryBrandRelationServiceImpl extends ServiceImpl<CategoryBrandRelationMapper, CategoryBrandRelation>
        implements ICategoryBrandRelationService {

    private final BrandMapper brandMapper;
    private final CategoryMapper categoryMapper;

    @Override
    public List<BrandRelationVO> listByBrandId(Long brandId) {
        // 校验品牌存在
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }

        List<CategoryBrandRelation> relations = baseMapper.selectList(
                new LambdaQueryWrapper<CategoryBrandRelation>()
                        .eq(CategoryBrandRelation::getBrandId, brandId)
                        .orderByAsc(CategoryBrandRelation::getId));

        return relations.stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRelation(BrandCategoryRelationSaveDTO dto) {
        Long brandId = dto.getBrandId();
        Long categoryId = dto.getCategoryId();

        // 1. 校验品牌存在
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }

        // 2. 校验分类存在且为三级
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || category.getLevel() == null || category.getLevel() != 3) {
            throw new BizException(ResultCode.BRAND_CATEGORY_INVALID,
                    "关联分类 [" + categoryId + "] 不存在或非三级分类");
        }

        // 3. 校验关联不重复（@TableLogic 自动过滤已逻辑删除记录）
        long existCount = baseMapper.selectCount(
                new LambdaQueryWrapper<CategoryBrandRelation>()
                        .eq(CategoryBrandRelation::getBrandId, brandId)
                        .eq(CategoryBrandRelation::getCategoryId, categoryId));
        if (existCount > 0) {
            throw new BizException(ResultCode.BRAND_RELATION_DUPLICATE);
        }

        // 4. 写入关联，冗余存储当前品牌名/分类名
        CategoryBrandRelation relation = new CategoryBrandRelation();
        relation.setBrandId(brandId);
        relation.setCategoryId(categoryId);
        relation.setBrandName(brand.getName());
        relation.setCategoryName(category.getName());
        baseMapper.insert(relation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRelation(Long brandId, Long categoryId) {
        CategoryBrandRelation relation = baseMapper.selectOne(
                new LambdaQueryWrapper<CategoryBrandRelation>()
                        .eq(CategoryBrandRelation::getBrandId, brandId)
                        .eq(CategoryBrandRelation::getCategoryId, categoryId));
        if (relation == null) {
            throw new BizException(ResultCode.BRAND_RELATION_NOT_FOUND);
        }
        baseMapper.deleteById(relation.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBrandName(Long brandId, String newName) {
        baseMapper.update(null, new LambdaUpdateWrapper<CategoryBrandRelation>()
                .eq(CategoryBrandRelation::getBrandId, brandId)
                .set(CategoryBrandRelation::getBrandName, newName));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategoryName(Long categoryId, String newCategoryName) {
        baseMapper.update(null, new LambdaUpdateWrapper<CategoryBrandRelation>()
                .eq(CategoryBrandRelation::getCategoryId, categoryId)
                .set(CategoryBrandRelation::getCategoryName, newCategoryName));
    }

    private BrandRelationVO toVO(CategoryBrandRelation relation) {
        BrandRelationVO vo = new BrandRelationVO();
        vo.setId(relation.getId());
        vo.setBrandId(relation.getBrandId());
        vo.setBrandName(relation.getBrandName());
        vo.setCategoryId(relation.getCategoryId());
        vo.setCategoryName(relation.getCategoryName());
        return vo;
    }
}
