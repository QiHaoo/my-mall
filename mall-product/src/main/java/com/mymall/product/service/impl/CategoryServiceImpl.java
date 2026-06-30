package com.mymall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mymall.common.exception.BizException;
import com.mymall.common.exception.ResultCode;
import com.mymall.product.dto.category.*;
import com.mymall.product.entity.Category;
import com.mymall.product.entity.CategoryBrandRelation;
import com.mymall.product.entity.SpuInfo;
import com.mymall.product.mapper.CategoryBrandRelationMapper;
import com.mymall.product.mapper.CategoryMapper;
import com.mymall.product.mapper.SpuInfoMapper;
import com.mymall.product.service.ICategoryBrandRelationService;
import com.mymall.product.service.ICategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品三级分类服务实现类
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

    private final CategoryBrandRelationMapper categoryBrandRelationMapper;
    private final SpuInfoMapper spuInfoMapper;
    private final ICategoryBrandRelationService categoryBrandRelationService;

    // ==================== 查询 ====================

    @Override
    public List<CategoryVO> listTree() {
        // 1. 一次查出所有可见分类
        List<Category> allCategories = list(new LambdaQueryWrapper<Category>()
                .eq(Category::getShowStatus, 1)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));

        // 2. 转换为 VO
        List<CategoryVO> allVOs = allCategories.stream()
                .map(this::toVO)
                .toList();

        // 3. 按 parentId 分组
        Map<Long, List<CategoryVO>> parentMap = allVOs.stream()
                .collect(Collectors.groupingBy(CategoryVO::getParentId));

        // 4. 递归组装树（一级分类的 parentId = 0）
        return buildTree(parentMap, 0L);
    }

    // ==================== 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCategory(CategorySaveDTO dto) {
        int level;

        if (dto.getParentId() == 0L) {
            // 一级分类
            level = 1;
        } else {
            // 查询父分类
            Category parent = getById(dto.getParentId());
            if (parent == null) {
                throw new BizException(ResultCode.CATEGORY_NOT_FOUND, "父分类不存在");
            }
            level = parent.getLevel() + 1;
            if (level > 3) {
                throw new BizException(ResultCode.CATEGORY_LEVEL_EXCEEDED);
            }
        }

        // 检查同级名称唯一
        checkNameUnique(dto.getParentId(), dto.getName(), null);

        // 构建实体并插入
        Category category = new Category();
        category.setName(dto.getName());
        category.setParentId(dto.getParentId());
        category.setLevel(level);
        category.setSort(dto.getSort() != null ? dto.getSort() : 0);
        category.setIcon(dto.getIcon());
        category.setProductUnit(dto.getProductUnit());
        category.setShowStatus(1);
        category.setProductCount(0);

        save(category);
    }

    // ==================== 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(CategoryUpdateDTO dto) {
        Long id = dto.getId();
        Category existing = getById(id);
        if (existing == null) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }

        boolean nameChanged = dto.getName() != null && !dto.getName().equals(existing.getName());
        // 如果名称有变化，检查同级唯一
        if (nameChanged) {
            checkNameUnique(existing.getParentId(), dto.getName(), id);
        }

        // 只更新非 null 字段
        if (dto.getName() != null) existing.setName(dto.getName());
        if (dto.getSort() != null) existing.setSort(dto.getSort());
        if (dto.getIcon() != null) existing.setIcon(dto.getIcon());
        if (dto.getProductUnit() != null) existing.setProductUnit(dto.getProductUnit());

        updateById(existing);

        // 分类名变更 → 同步刷新关联表冗余 category_name
        if (nameChanged) {
            categoryBrandRelationService.updateCategoryName(id, existing.getName());
        }
    }

    // ==================== 批量删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        // 1. 查询所有待删除分类
        List<Category> categories = listByIds(ids);
        if (categories.isEmpty()) {
            return;
        }

        // 2. 检查一级分类保护
        for (Category cat : categories) {
            if (cat.getParentId() == 0L) {
                throw new BizException(ResultCode.CATEGORY_ROOT_DELETE,
                        "一级分类 [" + cat.getName() + "] 不允许删除");
            }
        }

        // 3. 查出所有分类（用于递归子孙）
        List<Category> allCategories = list();
        Map<Long, List<Category>> childrenMap = allCategories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

        // 4. 递归收集所有子孙分类 ID
        Set<Long> allIdsToDelete = new LinkedHashSet<>(ids);
        for (Long id : ids) {
            collectDescendantIds(id, childrenMap, allIdsToDelete);
        }

        // 5. 检查引用关系
        checkCategoryReferences(allIdsToDelete);

        // 6. 业务逻辑删除：show_status 置为 0（隐藏）
        update(new LambdaUpdateWrapper<Category>()
                .in(Category::getId, allIdsToDelete)
                .set(Category::getShowStatus, 0));
    }

    /**
     * 检查待删除分类（含子孙）是否存在外部引用。
     * <p>
     * 当前检查本服务内的商品、品牌关联；
     * 优惠券分类关联（sms_coupon_spu_category_relation）位于 mall-coupon 服务，
     * 待后续通过 Feign 接入后补充校验。
     *
     * @param allIdsToDelete 待删除分类 ID 集合
     */
    private void checkCategoryReferences(Set<Long> allIdsToDelete) {
        // 5.1 商品引用检查：pms_spu_info.category_id（@TableLogic 自动过滤已删除 SPU）
        long productCount = spuInfoMapper.selectCount(
                new LambdaQueryWrapper<SpuInfo>()
                        .in(SpuInfo::getCategoryId, allIdsToDelete));
        if (productCount > 0) {
            throw new BizException(ResultCode.CATEGORY_HAS_PRODUCTS,
                    "分类下存在关联商品，无法删除");
        }

        // 5.2 品牌关联检查：pms_category_brand_relation.category_id
        for (Long id : allIdsToDelete) {
            long relationCount = categoryBrandRelationMapper.selectCount(
                    new LambdaQueryWrapper<CategoryBrandRelation>()
                            .eq(CategoryBrandRelation::getCategoryId, id));
            if (relationCount > 0) {
                throw new BizException(ResultCode.CATEGORY_HAS_BRANDS,
                        "分类 [" + getCategoryNameById(id, allIdsToDelete) + "] 下存在关联品牌，无法删除");
            }
        }

        // 5.3 优惠券分类关联检查（mall-coupon 服务，待 Feign 接入）
        // TODO: 通过 Feign 调用 mall-coupon 服务校验 sms_coupon_spu_category_relation
    }

    /**
     * 根据分类 ID 从待删除集合中查找分类名称（用于错误提示）
     */
    private String getCategoryNameById(Long id, Set<Long> allIdsToDelete) {
        return list(new LambdaQueryWrapper<Category>().in(Category::getId, allIdsToDelete))
                .stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .map(Category::getName)
                .orElseGet(() -> String.valueOf(id));
    }

    // ==================== 拖拽排序 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortCategories(CategorySortDTO dto) {
        // 查出所有分类（用于循环引用检测）
        List<Category> allCategories = list();
        Map<Long, Category> categoryMap = allCategories.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        // 构建全局 parentMap，再叠加本次批次中所有待变更的父节点，检测批量内的隐式循环
        Map<Long, Long> parentMap = allCategories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getParentId));
        Map<Long, Long> workingParentMap = new HashMap<>(parentMap);
        for (CategorySortDTO.SortItem item : dto.getCategories()) {
            workingParentMap.put(item.getId(), item.getParentId());
        }

        List<Category> toUpdate = new ArrayList<>();

        for (CategorySortDTO.SortItem item : dto.getCategories()) {
            Long id = item.getId();
            Category existing = categoryMap.get(id);
            if (existing == null) {
                throw new BizException(ResultCode.CATEGORY_NOT_FOUND,
                        "分类ID [" + id + "] 不存在");
            }

            // 检查循环引用：新父节点不能是自己的子孙（基于叠加后的 workingParentMap）
            if (item.getParentId() != 0L) {
                checkCircularReference(id, item.getParentId(), workingParentMap);
            }

            // 构建更新对象
            Category update = new Category();
            update.setId(id);
            update.setParentId(item.getParentId());
            update.setLevel(item.getLevel());
            update.setSort(item.getSort());
            toUpdate.add(update);
        }

        updateBatchById(toUpdate);
    }

    // ==================== 内部方法 ====================

    /**
     * 检查同级名称唯一
     *
     * @param parentId 父分类 ID
     * @param name      分类名称
     * @param excludeId 排除的分类 ID（修改时排除自己）
     */
    private void checkNameUnique(Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, parentId)
                .eq(Category::getName, name);
        if (excludeId != null) {
            wrapper.ne(Category::getId, excludeId);
        }
        if (count(wrapper) > 0) {
            throw new BizException(ResultCode.CATEGORY_NAME_DUPLICATE);
        }
    }

    /**
     * 递归收集子孙分类 ID
     */
    private void collectDescendantIds(Long parentId, Map<Long, List<Category>> childrenMap, Set<Long> result) {
        List<Category> children = childrenMap.get(parentId);
        if (children == null) {
            return;
        }
        for (Category child : children) {
            result.add(child.getId());
            collectDescendantIds(child.getId(), childrenMap, result);
        }
    }

    /**
     * 检查循环引用：targetParentId 不能是 nodeId 的子孙
     *
     * @param nodeId        当前节点 ID
     * @param targetParentId 拟变更的新父节点 ID
     * @param parentMap     全局父子关系映射（可叠加批次变更）
     */
    private void checkCircularReference(Long nodeId, Long targetParentId, Map<Long, Long> parentMap) {
        Long current = targetParentId;
        while (current != null && current != 0L) {
            if (current.equals(nodeId)) {
                throw new BizException(ResultCode.CATEGORY_CIRCULAR_REF);
            }
            current = parentMap.get(current);
        }
    }

    /**
     * 递归构建树
     */
    private List<CategoryVO> buildTree(Map<Long, List<CategoryVO>> parentMap, Long parentId) {
        List<CategoryVO> children = parentMap.getOrDefault(parentId, Collections.emptyList());
        for (CategoryVO child : children) {
            child.setChildren(buildTree(parentMap, child.getId()));
        }
        return children;
    }

    /**
     * Entity → VO 转换
     */
    private CategoryVO toVO(Category entity) {
        CategoryVO vo = new CategoryVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setParentId(entity.getParentId());
        vo.setLevel(entity.getLevel());
        vo.setShowStatus(entity.getShowStatus() != null ? entity.getShowStatus().intValue() : 1);
        vo.setSort(entity.getSort());
        vo.setIcon(entity.getIcon());
        vo.setProductUnit(entity.getProductUnit());
        vo.setProductCount(entity.getProductCount());
        return vo;
    }
}
