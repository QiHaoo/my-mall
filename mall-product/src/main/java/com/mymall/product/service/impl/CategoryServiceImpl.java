package com.mymall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mymall.common.exception.BizException;
import com.mymall.common.exception.ResultCode;
import com.mymall.product.dto.category.*;
import com.mymall.product.entity.Category;
import com.mymall.product.entity.CategoryBrandRelation;
import com.mymall.product.mapper.CategoryBrandRelationMapper;
import com.mymall.product.mapper.CategoryMapper;
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

        // 3. 按 parentCid 分组
        Map<Long, List<CategoryVO>> parentMap = allVOs.stream()
                .collect(Collectors.groupingBy(CategoryVO::getParentCid));

        // 4. 递归组装树（一级分类的 parentCid = 0）
        return buildTree(parentMap, 0L);
    }

    // ==================== 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCategory(CategorySaveDTO dto) {
        int catLevel;

        if (dto.getParentCid() == 0L) {
            // 一级分类
            catLevel = 1;
        } else {
            // 查询父分类
            Category parent = getById(dto.getParentCid());
            if (parent == null) {
                throw new BizException(ResultCode.CATEGORY_NOT_FOUND, "父分类不存在");
            }
            catLevel = parent.getCatLevel() + 1;
            if (catLevel > 3) {
                throw new BizException(ResultCode.CATEGORY_LEVEL_EXCEEDED);
            }
        }

        // 检查同级名称唯一
        checkNameUnique(dto.getParentCid(), dto.getName(), null);

        // 构建实体并插入
        Category category = new Category();
        category.setName(dto.getName());
        category.setParentCid(dto.getParentCid());
        category.setCatLevel(catLevel);
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
        Category existing = getById(dto.getId());
        if (existing == null) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }

        // 如果名称有变化，检查同级唯一
        if (dto.getName() != null && !dto.getName().equals(existing.getName())) {
            checkNameUnique(existing.getParentCid(), dto.getName(), dto.getId());
        }

        // 只更新非 null 字段
        if (dto.getName() != null) existing.setName(dto.getName());
        if (dto.getSort() != null) existing.setSort(dto.getSort());
        if (dto.getIcon() != null) existing.setIcon(dto.getIcon());
        if (dto.getProductUnit() != null) existing.setProductUnit(dto.getProductUnit());

        updateById(existing);
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
            if (cat.getParentCid() == 0L) {
                throw new BizException(ResultCode.CATEGORY_ROOT_DELETE,
                        "一级分类 [" + cat.getName() + "] 不允许删除");
            }
        }

        // 3. 查出所有分类（用于递归子孙）
        List<Category> allCategories = list();
        Map<Long, List<Category>> childrenMap = allCategories.stream()
                .collect(Collectors.groupingBy(Category::getParentCid));

        // 4. 递归收集所有子孙分类 ID
        Set<Long> allIdsToDelete = new LinkedHashSet<>(ids);
        for (Long id : ids) {
            collectDescendantIds(id, childrenMap, allIdsToDelete);
        }

        // 5. 检查品牌关联引用
        for (Long id : allIdsToDelete) {
            long relationCount = categoryBrandRelationMapper.selectCount(
                    new LambdaQueryWrapper<CategoryBrandRelation>()
                            .eq(CategoryBrandRelation::getCatelogId, id));
            if (relationCount > 0) {
                throw new BizException(ResultCode.CATEGORY_HAS_BRANDS,
                        "分类ID [" + id + "] 下存在关联品牌，无法删除");
            }
        }

        // 6. 逻辑删除：BaseEntity 的 is_deleted 由 @TableLogic 自动置 1
        removeByIds(allIdsToDelete);
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
                .collect(Collectors.toMap(Category::getId, Category::getParentCid));
        Map<Long, Long> workingParentMap = new HashMap<>(parentMap);
        for (CategorySortDTO.SortItem item : dto.getCategories()) {
            workingParentMap.put(item.getId(), item.getParentCid());
        }

        List<Category> toUpdate = new ArrayList<>();

        for (CategorySortDTO.SortItem item : dto.getCategories()) {
            Category existing = categoryMap.get(item.getId());
            if (existing == null) {
                throw new BizException(ResultCode.CATEGORY_NOT_FOUND,
                        "分类ID [" + item.getId() + "] 不存在");
            }

            // 检查循环引用：新父节点不能是自己的子孙（基于叠加后的 workingParentMap）
            if (item.getParentCid() != 0L) {
                checkCircularReference(item.getId(), item.getParentCid(), workingParentMap);
            }

            // 构建更新对象
            Category update = new Category();
            update.setId(item.getId());
            update.setParentCid(item.getParentCid());
            update.setCatLevel(item.getCatLevel());
            update.setSort(item.getSort());
            toUpdate.add(update);
        }

        updateBatchById(toUpdate);
    }

    // ==================== 内部方法 ====================

    /**
     * 检查同级名称唯一
     *
     * @param parentCid 父分类 ID
     * @param name      分类名称
     * @param excludeId 排除的分类 ID（修改时排除自己）
     */
    private void checkNameUnique(Long parentCid, String name, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .eq(Category::getParentCid, parentCid)
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
        vo.setParentCid(entity.getParentCid());
        vo.setCatLevel(entity.getCatLevel());
        vo.setShowStatus(entity.getShowStatus() != null ? entity.getShowStatus().intValue() : 1);
        vo.setSort(entity.getSort());
        vo.setIcon(entity.getIcon());
        vo.setProductUnit(entity.getProductUnit());
        vo.setProductCount(entity.getProductCount());
        return vo;
    }
}
