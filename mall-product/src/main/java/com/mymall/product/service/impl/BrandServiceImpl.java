package com.mymall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mymall.common.exception.BizException;
import com.mymall.common.exception.ResultCode;
import com.mymall.common.result.PageVO;
import com.mymall.common.util.PageUtils;
import com.mymall.product.dto.brand.BrandBatchDeleteDTO;
import com.mymall.product.dto.brand.BrandQueryDTO;
import com.mymall.product.dto.brand.BrandSaveDTO;
import com.mymall.product.dto.brand.BrandShowStatusDTO;
import com.mymall.product.dto.brand.BrandSimpleVO;
import com.mymall.product.dto.brand.BrandVO;
import com.mymall.product.entity.Brand;
import com.mymall.product.entity.CategoryBrandRelation;
import com.mymall.product.entity.SpuInfo;
import com.mymall.product.mapper.BrandMapper;
import com.mymall.product.mapper.CategoryBrandRelationMapper;
import com.mymall.product.mapper.SpuInfoMapper;
import com.mymall.product.service.IBrandService;
import com.mymall.product.service.ICategoryBrandRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * 品牌服务实现
 *
 * <p>涉及 {@code pms_brand} 与 {@code pms_category_brand_relation} 双表，写操作均在事务内。
 * 逻辑删除由 {@code @TableLogic} 自动处理，乐观锁由 {@code @Version} + OptimisticLockerInnerInterceptor 处理。
 * 关联分类管理由 {@link ICategoryBrandRelationService} 独立承担，品牌新增/修改不处理关联分类。
 */
@Service
@RequiredArgsConstructor
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements IBrandService {

    private final CategoryBrandRelationMapper categoryBrandRelationMapper;
    private final SpuInfoMapper spuInfoMapper;
    private final ICategoryBrandRelationService categoryBrandRelationService;

    // ==================== 查询 ====================

    @Override
    public PageVO<BrandVO> pageQuery(BrandQueryDTO query) {
        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<Brand>()
                .like(isNotBlank(query.getName()), Brand::getName, query.getName())
                .eq(isNotBlank(query.getFirstLetter()), Brand::getFirstLetter,
                        query.getFirstLetter() == null ? null : query.getFirstLetter().toUpperCase())
                .eq(query.getShowStatus() != null, Brand::getShowStatus, query.getShowStatus())
                .orderByAsc(Brand::getSort)
                .orderByAsc(Brand::getId);
        Page<Brand> result = page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        List<BrandVO> voList = result.getRecords().stream().map(this::toVO).toList();
        return PageUtils.toPageVO(result, voList);
    }

    @Override
    public BrandVO getBrandDetail(Long id) {
        Brand brand = getById(id);
        if (brand == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }
        return toVO(brand);
    }

    @Override
    public List<BrandSimpleVO> listByCategory(Long categoryId) {
        List<CategoryBrandRelation> relations = categoryBrandRelationMapper.selectList(
                new LambdaQueryWrapper<CategoryBrandRelation>()
                        .eq(CategoryBrandRelation::getCategoryId, categoryId));
        if (CollectionUtils.isEmpty(relations)) {
            return Collections.emptyList();
        }
        List<Long> brandIds = relations.stream()
                .map(CategoryBrandRelation::getBrandId).distinct().toList();
        List<Brand> brands = list(new LambdaQueryWrapper<Brand>()
                .in(Brand::getId, brandIds)
                .eq(Brand::getShowStatus, 1)
                .orderByAsc(Brand::getSort)
                .orderByAsc(Brand::getId));
        return brands.stream().map(this::toSimpleVO).toList();
    }

    // ==================== 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBrand(BrandSaveDTO dto) {
        // 品牌名全局唯一
        if (count(new LambdaQueryWrapper<Brand>().eq(Brand::getName, dto.getName())) > 0) {
            throw new BizException(ResultCode.BRAND_NAME_DUPLICATE);
        }

        Brand brand = new Brand();
        BeanUtils.copyProperties(dto, brand);
        if (brand.getFirstLetter() != null) {
            brand.setFirstLetter(brand.getFirstLetter().toUpperCase());
        }
        if (brand.getShowStatus() == null) {
            brand.setShowStatus(1);
        }
        save(brand);
    }

    // ==================== 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBrand(BrandSaveDTO dto) {
        Brand existing = getById(dto.getId());
        if (existing == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }

        boolean nameChanged = dto.getName() != null && !dto.getName().equals(existing.getName());
        if (nameChanged && count(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getName, dto.getName())
                .ne(Brand::getId, dto.getId())) > 0) {
            throw new BizException(ResultCode.BRAND_NAME_DUPLICATE);
        }

        Brand brand = new Brand();
        BeanUtils.copyProperties(dto, brand);
        if (brand.getFirstLetter() != null) {
            brand.setFirstLetter(brand.getFirstLetter().toUpperCase());
        }
        // updateById 携带 version 触发乐观锁；非 null 字段更新（NOT_NULL 策略）
        updateById(brand);

        // 品牌名变更 → 同步刷新关联表冗余 brand_name
        if (nameChanged) {
            categoryBrandRelationService.updateBrandName(dto.getId(), dto.getName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShowStatus(Long id, BrandShowStatusDTO dto) {
        Brand existing = getById(id);
        if (existing == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }
        Integer status = dto.getShowStatus();
        // 防御性校验：Controller 已由 @Min/@Max 拦截，此处兜底服务直调场景
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ResultCode.BRAND_SHOW_STATUS_INVALID);
        }
        Brand update = new Brand();
        update.setId(id);
        update.setShowStatus(status);
        update.setVersion(existing.getVersion());
        updateById(update);
    }

    // ==================== 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeBrand(Long id) {
        Brand brand = getById(id);
        if (brand == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }
        checkProductReference(id, brand.getName());
        removeById(id);
        categoryBrandRelationMapper.delete(new LambdaQueryWrapper<CategoryBrandRelation>()
                .eq(CategoryBrandRelation::getBrandId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(BrandBatchDeleteDTO dto) {
        List<Long> ids = dto.getIds();
        if (CollectionUtils.isEmpty(ids)) {
            throw new BizException(ResultCode.BRAND_BATCH_DELETE_EMPTY);
        }

        // 逐个检查引用，任一品牌存在关联商品则整体回滚
        for (Long id : ids) {
            Brand brand = getById(id);
            if (brand == null) {
                throw new BizException(ResultCode.BRAND_NOT_FOUND);
            }
            checkProductReference(id, brand.getName());
        }

        // 统一逻辑删除品牌及关联
        removeByIds(ids);
        categoryBrandRelationMapper.delete(new LambdaQueryWrapper<CategoryBrandRelation>()
                .in(CategoryBrandRelation::getBrandId, ids));
    }

    // ==================== 内部方法 ====================

    /**
     * 检查品牌下是否存在关联商品
     *
     * @param brandId   品牌ID
     * @param brandName 品牌名（用于错误提示）
     */
    private void checkProductReference(Long brandId, String brandName) {
        long spuCount = spuInfoMapper.selectCount(
                new LambdaQueryWrapper<SpuInfo>().eq(SpuInfo::getBrandId, brandId));
        if (spuCount > 0) {
            throw new BizException(ResultCode.BRAND_HAS_PRODUCTS,
                    "品牌 [" + brandName + "] 下存在关联商品，无法删除");
        }
    }

    private BrandVO toVO(Brand brand) {
        BrandVO vo = new BrandVO();
        BeanUtils.copyProperties(brand, vo);
        return vo;
    }

    private BrandSimpleVO toSimpleVO(Brand brand) {
        BrandSimpleVO vo = new BrandSimpleVO();
        vo.setId(brand.getId());
        vo.setName(brand.getName());
        vo.setLogo(brand.getLogo());
        vo.setFirstLetter(brand.getFirstLetter());
        return vo;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
