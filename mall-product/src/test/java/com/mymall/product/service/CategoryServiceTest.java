package com.mymall.product.service;

import com.mymall.common.exception.BizException;
import com.mymall.common.exception.ResultCode;
import com.mymall.product.dto.category.*;
import com.mymall.product.entity.Category;
import com.mymall.product.mapper.CategoryBrandRelationMapper;
import com.mymall.product.mapper.CategoryMapper;
import com.mymall.product.mapper.SpuInfoMapper;
import com.mymall.product.service.impl.CategoryServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CategoryService 纯单元测试
 * <p>
 * 不加载 Spring 上下文，Mock 所有依赖，速度极快。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("商品分类服务")
class CategoryServiceTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Category.class);
    }

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private CategoryBrandRelationMapper categoryBrandRelationMapper;

    @Mock
    private SpuInfoMapper spuInfoMapper;

    @Mock
    private ICategoryBrandRelationService categoryBrandRelationService;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        // ServiceImpl.baseMapper is set by Spring @Autowired field injection.
        // Mockito @InjectMocks only uses the @RequiredArgsConstructor constructor,
        // so the parent class field stays null. Set it manually via reflection.
        categoryService = new CategoryServiceImpl(categoryBrandRelationMapper, spuInfoMapper, categoryBrandRelationService);
        ReflectionTestUtils.setField(categoryService, "baseMapper", categoryMapper);
    }

    // ==================== 分类树查询 ====================

    @Nested
    @DisplayName("查询分类树")
    class ListTree {

        @Test
        @DisplayName("空数据时应返回空列表")
        void shouldReturnEmptyWhenNoData() {
            // Given
            lenient().when(categoryMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When
            List<CategoryVO> result = categoryService.listTree();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应正确组装一级分类")
        void shouldBuildLevelOneCategories() {
            // Given
            Category cat1 = buildCategory(1L, "图书", 0L, 1, 0);
            Category cat2 = buildCategory(2L, "手机", 0L, 1, 1);
            when(categoryMapper.selectList(any())).thenReturn(List.of(cat1, cat2));

            // When
            List<CategoryVO> result = categoryService.listTree();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting("name").containsExactly("图书", "手机");
        }

        @Test
        @DisplayName("应正确组装嵌套子分类")
        void shouldBuildNestedTree() {
            // Given
            Category parent = buildCategory(1L, "图书", 0L, 1, 0);
            Category child = buildCategory(2L, "电子书刊", 1L, 2, 0);
            Category grandChild = buildCategory(3L, "电子书", 2L, 3, 0);
            when(categoryMapper.selectList(any())).thenReturn(List.of(parent, child, grandChild));

            // When
            List<CategoryVO> result = categoryService.listTree();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getChildren()).hasSize(1);
            assertThat(result.get(0).getChildren().get(0).getChildren()).hasSize(1);
            assertThat(result.get(0).getChildren().get(0).getChildren().get(0).getName()).isEqualTo("电子书");
        }
    }

    // ==================== 新增分类 ====================

    @Nested
    @DisplayName("新增分类")
    class SaveCategory {

        @Test
        @DisplayName("新增一级分类应成功")
        void shouldSaveLevelOneCategory() {
            // Given
            CategorySaveDTO dto = new CategorySaveDTO();
            dto.setName("新分类");
            dto.setParentId(0L);
            dto.setSort(1);

            // Mock count 返回 0（无重复名称）
            lenient().when(categoryMapper.selectCount(any())).thenReturn(0L);
            lenient().when(categoryMapper.insert(any(Category.class))).thenReturn(1);

            // When & Then
            assertThatCode(() -> categoryService.saveCategory(dto))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("父分类不存在时应抛异常")
        void shouldThrowWhenParentNotFound() {
            // Given
            CategorySaveDTO dto = new CategorySaveDTO();
            dto.setName("子分类");
            dto.setParentId(999L);

            when(categoryMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> categoryService.saveCategory(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.CATEGORY_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("超过三级时应抛异常")
        void shouldThrowWhenLevelExceeded() {
            // Given: 父分类已经是第三级
            Category parent = buildCategory(1L, "三级分类", 2L, 3, 0);
            when(categoryMapper.selectById(1L)).thenReturn(parent);

            CategorySaveDTO dto = new CategorySaveDTO();
            dto.setName("四级分类");
            dto.setParentId(1L);

            // When & Then
            assertThatThrownBy(() -> categoryService.saveCategory(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.CATEGORY_LEVEL_EXCEEDED.getCode());
        }
    }

    // ==================== 修改分类 ====================

    @Nested
    @DisplayName("修改分类")
    class UpdateCategory {

        @Test
        @DisplayName("分类不存在时应抛异常")
        void shouldThrowWhenCategoryNotFound() {
            // Given
            CategoryUpdateDTO dto = new CategoryUpdateDTO();
            dto.setId(999L);
            dto.setName("新名称");

            when(categoryMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> categoryService.updateCategory(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.CATEGORY_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("改名时应同步刷新关联表冗余分类名")
        void shouldSyncRelationCatelogNameWhenRenamed() {
            // Given
            CategoryUpdateDTO dto = new CategoryUpdateDTO();
            dto.setId(1L);
            dto.setName("新名称");

            Category existing = buildCategory(1L, "旧名称", 0L, 1, 0);
            when(categoryMapper.selectById(1L)).thenReturn(existing);
            lenient().when(categoryMapper.selectCount(any())).thenReturn(0L);
            when(categoryMapper.updateById(any(Category.class))).thenReturn(1);

            // When
            categoryService.updateCategory(dto);

            // Then
            verify(categoryBrandRelationService).updateCategoryName(1L, "新名称");
        }
    }

    // ==================== 批量删除 ====================

    @Nested
    @DisplayName("批量删除")
    class BatchDelete {

        @Test
        @DisplayName("删除一级分类应被拒绝")
        void shouldRejectRootCategoryDeletion() {
            // Given
            Category root = buildCategory(1L, "一级分类", 0L, 1, 0);
            when(categoryMapper.selectByIds(anyList())).thenReturn(List.of(root));

            // When & Then
            assertThatThrownBy(() -> categoryService.batchDelete(List.of(1L)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.CATEGORY_ROOT_DELETE.getCode());
        }

        @Test
        @DisplayName("空ID列表应直接返回")
        void shouldReturnWhenEmptyIds() {
            // When & Then: ServiceImpl.listByIds() 对空集合直接返回，不调用 mapper
            assertThatCode(() -> categoryService.batchDelete(Collections.emptyList()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("分类下存在商品时应拒绝删除")
        void shouldRejectWhenProductExists() {
            // Given: 二级分类 ID=2（父为一级 ID=1）
            Category child = buildCategory(2L, "电子书刊", 1L, 2, 0);
            when(categoryMapper.selectByIds(anyList())).thenReturn(List.of(child));

            // Mock allCategories（用于收集子孙）
            Category root = buildCategory(1L, "图书", 0L, 1, 0);
            when(categoryMapper.selectList(any())).thenReturn(List.of(root, child));

            // Mock 商品引用检查：存在关联商品
            when(spuInfoMapper.selectCount(any())).thenReturn(1L);

            // When & Then
            assertThatThrownBy(() -> categoryService.batchDelete(List.of(2L)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.CATEGORY_HAS_PRODUCTS.getCode());
        }

        @Test
        @DisplayName("分类下存在品牌关联时应拒绝删除")
        void shouldRejectWhenBrandRelationExists() {
            // Given: 二级分类 ID=2（父为一级 ID=1）
            Category child = buildCategory(2L, "电子书刊", 1L, 2, 0);
            when(categoryMapper.selectByIds(anyList())).thenReturn(List.of(child));

            // Mock allCategories（用于收集子孙）
            Category root = buildCategory(1L, "图书", 0L, 1, 0);
            when(categoryMapper.selectList(any())).thenReturn(List.of(root, child));

            // Mock 商品引用检查通过，品牌关联检查：ID=2 存在品牌关联
            when(spuInfoMapper.selectCount(any())).thenReturn(0L);
            when(categoryBrandRelationMapper.selectCount(any())).thenReturn(1L);

            // When & Then
            assertThatThrownBy(() -> categoryService.batchDelete(List.of(2L)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.CATEGORY_HAS_BRANDS.getCode());
        }

        @Test
        @DisplayName("无引用时应将 show_status 置为 0")
        void shouldSetShowStatusToZeroWhenNoReferences() {
            // Given: 二级分类 ID=2（父为一级 ID=1）
            Category child = buildCategory(2L, "电子书刊", 1L, 2, 0);
            when(categoryMapper.selectByIds(anyList())).thenReturn(List.of(child));

            Category root = buildCategory(1L, "图书", 0L, 1, 0);
            when(categoryMapper.selectList(any())).thenReturn(List.of(root, child));

            when(spuInfoMapper.selectCount(any())).thenReturn(0L);
            when(categoryBrandRelationMapper.selectCount(any())).thenReturn(0L);
            when(categoryMapper.update(isNull(), any())).thenReturn(1);

            // When & Then
            assertThatCode(() -> categoryService.batchDelete(List.of(2L)))
                    .doesNotThrowAnyException();
            verify(categoryMapper).update(isNull(), any());
        }
    }

    // ==================== 拖拽排序 ====================

    @Nested
    @DisplayName("拖拽排序")
    class SortCategories {

        @Test
        @DisplayName("批量操作形成隐式循环时应抛异常")
        void shouldThrowWhenBatchCreatesCircularRef() {
            // Given: 当前树结构 A(1)->B(2)->C(3), A(1)->D(4)
            Category a = buildCategory(1L, "A", 0L, 1, 0);
            Category b = buildCategory(2L, "B", 1L, 2, 1);
            Category c = buildCategory(3L, "C", 2L, 3, 2);
            Category d = buildCategory(4L, "D", 1L, 2, 3);
            when(categoryMapper.selectList(any())).thenReturn(List.of(a, b, c, d));

            // 批量拖拽：D 移到 C 下，B 移到 D 下
            // 结果会形成 B->D->C->B 循环
            CategorySortDTO.SortItem item1 = new CategorySortDTO.SortItem();
            item1.setId(4L);   // D
            item1.setParentId(3L); // 移到 C 下
            item1.setLevel(4);
            item1.setSort(0);

            CategorySortDTO.SortItem item2 = new CategorySortDTO.SortItem();
            item2.setId(2L);   // B
            item2.setParentId(4L); // 移到 D 下
            item2.setLevel(3);
            item2.setSort(1);

            CategorySortDTO dto = new CategorySortDTO();
            dto.setCategories(List.of(item1, item2));

            // When & Then
            assertThatThrownBy(() -> categoryService.sortCategories(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.CATEGORY_CIRCULAR_REF.getCode());
        }
    }

    // ==================== 辅助方法 ====================

    private Category buildCategory(Long id, String name, Long parentId, int level, int sort) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setParentId(parentId);
        category.setLevel(level);
        category.setSort(sort);
        category.setShowStatus(1);
        category.setProductCount(0);
        return category;
    }
}
