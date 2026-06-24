package com.mymall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mymall.common.exception.BizException;
import com.mymall.common.exception.ResultCode;
import com.mymall.product.dto.brand.BrandQueryDTO;
import com.mymall.product.dto.brand.BrandSaveDTO;
import com.mymall.product.dto.brand.BrandShowStatusDTO;
import com.mymall.product.dto.brand.BrandSimpleVO;
import com.mymall.product.dto.brand.BrandVO;
import com.mymall.product.entity.Brand;
import com.mymall.product.entity.Category;
import com.mymall.product.entity.CategoryBrandRelation;
import com.mymall.product.entity.SpuInfo;
import com.mymall.product.mapper.BrandMapper;
import com.mymall.product.mapper.CategoryBrandRelationMapper;
import com.mymall.product.mapper.CategoryMapper;
import com.mymall.product.mapper.SpuInfoMapper;
import com.mymall.product.service.IBrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 品牌服务实现
 *
 * <p>涉及 {@code pms_brand} 与 {@code pms_category_brand_relation} 双表，写操作均在事务内。
 * 逻辑删除由 {@code @TableLogic} 自动处理，乐观锁由 {@code @Version} + OptimisticLockerInnerInterceptor 处理。
 */
@Service
@RequiredArgsConstructor
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements IBrandService {

    private final CategoryBrandRelationMapper categoryBrandRelationMapper;
    private final SpuInfoMapper spuInfoMapper;
    private final CategoryMapper categoryMapper;

    // ==================== 查询 ====================

    @Override
    public Page<BrandVO> pageQuery(BrandQueryDTO query) {
        Page<Brand> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<Brand>()
                .like(isNotBlank(query.getName()), Brand::getName, query.getName())
                .eq(isNotBlank(query.getFirstLetter()), Brand::getFirstLetter,
                        query.getFirstLetter() == null ? null : query.getFirstLetter().toUpperCase())
                .eq(query.getShowStatus() != null, Brand::getShowStatus, query.getShowStatus())
                .orderByAsc(Brand::getSort)
                .orderByAsc(Brand::getId);
        Page<Brand> result = page(page, wrapper);

        Page<BrandVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public BrandVO getBrandDetail(Long id) {
        Brand brand = getById(id);
        if (brand == null) {
            throw new BizException(ResultCode.BRAND_NOT_FOUND);
        }
        BrandVO vo = toVO(brand);
        List<CategoryBrandRelation> relations = categoryBrandRelationMapper.selectList(
                new LambdaQueryWrapper<CategoryBrandRelation>()
                        .eq(CategoryBrandRelation::getBrandId, id));
        vo.setCategoryIds(relations.stream().map(CategoryBrandRelation::getCatelogId).toList());
        return vo;
    }

    @Override
    public List<BrandSimpleVO> listByCategory(Long catelogId) {
        List<CategoryBrandRelation> relations = categoryBrandRelationMapper.selectList(
                new LambdaQueryWrapper<CategoryBrandRelation>()
                        .eq(CategoryBrandRelation::getCatelogId, catelogId));
        if (relations.isEmpty()) {
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

        // 关联分类（save 后 brand.id 已回填）
        syncRelations(brand.getId(), brand.getName(), dto.getCategoryIds());
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

        String effectiveName = brand.getName() != null ? brand.getName() : existing.getName();

        // 品牌名变更 → 同步刷新关联表冗余 brand_name
        if (nameChanged) {
            categoryBrandRelationMapper.update(null, new LambdaUpdateWrapper<CategoryBrandRelation>()
                    .eq(CategoryBrandRelation::getBrandId, dto.getId())
                    .set(CategoryBrandRelation::getBrandName, effectiveName));
        }

        // categoryIds 非 null 时全量覆盖关联（null 表示不变，空数组表示清空）
        if (dto.getCategoryIds() != null) {
            syncRelations(dto.getId(), effectiveName, dto.getCategoryIds());
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
        // 引用检查：存在关联商品则拒绝（@TableLogic 自动过滤已逻辑删除的 SPU）
        long spuCount = spuInfoMapper.selectCount(
                new LambdaQueryWrapper<SpuInfo>().eq(SpuInfo::getBrandId, id));
        if (spuCount > 0) {
            throw new BizException(ResultCode.BRAND_HAS_PRODUCTS,
                    "品牌 [" + brand.getName() + "] 下存在关联商品，无法删除");
        }
        removeById(id);
        categoryBrandRelationMapper.delete(new LambdaQueryWrapper<CategoryBrandRelation>()
                .eq(CategoryBrandRelation::getBrandId, id));
    }

    // ==================== 内部方法 ====================

    /**
     * 同步品牌-分类关联（diff：删除多余的、新增缺少的）
     *
     * <p>采用 diff 而非"全删再插"，规避逻辑删除与唯一约束 {@code uk_brand_catelog(brand_id, catelog_id)} 的冲突：
     * 若先逻辑删除某关联再插入相同 (brand_id, catelog_id) 会触发唯一键冲突。
     * <p>已知边界：跨操作复用历史已逻辑删除的相同分类对仍可能冲突，
     * 生产彻底解决建议将唯一索引改为 {@code uk_brand_catelog(brand_id, catelog_id, is_deleted)}。
     *
     * @param brandId     品牌ID
     * @param brandName   品牌名（冗余写入）
     * @param categoryIds 期望的关联分类列表；null 表示不处理，空数组表示清空
     */
    private void syncRelations(Long brandId, String brandName, List<Long> categoryIds) {
        if (categoryIds == null) {
            return;
        }
        Map<Long, String> nameMap = validateCategories(categoryIds);

        Set<Long> desired = new LinkedHashSet<>(categoryIds);
        List<CategoryBrandRelation> active = categoryBrandRelationMapper.selectList(
                new LambdaQueryWrapper<CategoryBrandRelation>()
                        .eq(CategoryBrandRelation::getBrandId, brandId));
        Set<Long> activeCats = active.stream()
                .map(CategoryBrandRelation::getCatelogId).collect(Collectors.toSet());

        // 逻辑删除多余的关联
        Set<Long> toRemove = new HashSet<>(activeCats);
        toRemove.removeAll(desired);
        if (!toRemove.isEmpty()) {
            categoryBrandRelationMapper.delete(new LambdaQueryWrapper<CategoryBrandRelation>()
                    .eq(CategoryBrandRelation::getBrandId, brandId)
                    .in(CategoryBrandRelation::getCatelogId, toRemove));
        }

        // 新增缺少的关联
        Set<Long> toAdd = new LinkedHashSet<>(desired);
        toAdd.removeAll(activeCats);
        for (Long catelogId : toAdd) {
            CategoryBrandRelation relation = new CategoryBrandRelation();
            relation.setBrandId(brandId);
            relation.setCatelogId(catelogId);
            relation.setBrandName(brandName);
            relation.setCatelogName(nameMap.get(catelogId));
            categoryBrandRelationMapper.insert(relation);
        }
    }

    /**
     * 校验关联分类全部存在且为三级分类，返回 id→name 映射
     */
    private Map<Long, String> validateCategories(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Category> categories = categoryMapper.selectByIds(categoryIds);
        if (categories.size() != new HashSet<>(categoryIds).size()) {
            throw new BizException(ResultCode.BRAND_CATEGORY_INVALID, "部分关联分类不存在");
        }
        Map<Long, String> nameMap = new HashMap<>();
        for (Category category : categories) {
            if (category.getCatLevel() == null || category.getCatLevel() != 3) {
                throw new BizException(ResultCode.BRAND_CATEGORY_INVALID,
                        "分类 [" + category.getName() + "] 非三级分类，无法关联");
            }
            nameMap.put(category.getId(), category.getName());
        }
        return nameMap;
    }

    private BrandVO toVO(Brand brand) {
        BrandVO vo = new BrandVO();
        vo.setId(brand.getId());
        vo.setName(brand.getName());
        vo.setLogo(brand.getLogo());
        vo.setDescript(brand.getDescript());
        vo.setShowStatus(brand.getShowStatus());
        vo.setFirstLetter(brand.getFirstLetter());
        vo.setSort(brand.getSort());
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
