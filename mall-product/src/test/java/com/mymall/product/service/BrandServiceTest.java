package com.mymall.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.mymall.product.mapper.BrandMapper;
import com.mymall.product.mapper.CategoryBrandRelationMapper;
import com.mymall.product.mapper.CategoryMapper;
import com.mymall.product.mapper.SpuInfoMapper;
import com.mymall.product.service.impl.BrandServiceImpl;
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
 * BrandService 纯单元测试
 * <p>
 * 不加载 Spring 上下文，Mock 所有依赖，速度极快。
 * <p>
 * {@link com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper#set} 在构建
 * set 子句时会立即解析 lambda 列名（query wrapper 的 eq/like 则延迟到生成 SQL 时），
 * 纯单元测试无 Spring 上下文、lambda 缓存未初始化会抛 "can not find lambda cache"。
 * 故 {@link #initLambdaCache()} 预热相关实体的 TableInfo 缓存。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("品牌服务")
class BrandServiceTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CategoryBrandRelation.class);
    }

    @Mock
    private BrandMapper brandMapper;

    @Mock
    private CategoryBrandRelationMapper categoryBrandRelationMapper;

    @Mock
    private SpuInfoMapper spuInfoMapper;

    @Mock
    private CategoryMapper categoryMapper;

    private BrandServiceImpl brandService;

    @BeforeEach
    void setUp() {
        // ServiceImpl.baseMapper 由 Spring 字段注入，@InjectMocks 仅走构造器，
        // 父类 baseMapper 字段保持 null，需反射注入。
        brandService = new BrandServiceImpl(categoryBrandRelationMapper, spuInfoMapper, categoryMapper);
        ReflectionTestUtils.setField(brandService, "baseMapper", brandMapper);
    }

    // ==================== 分页查询 ====================

    @Nested
    @DisplayName("分页查询")
    class PageQuery {

        @Test
        @DisplayName("应返回分页结果并映射为 VO")
        void shouldReturnPagedVOs() {
            // Given
            BrandQueryDTO query = new BrandQueryDTO();
            Brand brand = buildBrand(1L, "小米", 1, "X", 0);
            when(brandMapper.selectPage(any(), any())).thenAnswer(inv -> {
                Page<Brand> p = inv.getArgument(0);
                p.setRecords(List.of(brand));
                p.setTotal(1);
                return p;
            });

            // When
            PageVO<BrandVO> result = brandService.pageQuery(query);

            // Then
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getName()).isEqualTo("小米");
        }
    }

    // ==================== 品牌详情 ====================

    @Nested
    @DisplayName("品牌详情")
    class GetBrandDetail {

        @Test
        @DisplayName("品牌不存在时应抛异常")
        void shouldThrowWhenNotFound() {
            // Given
            when(brandMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> brandService.getBrandDetail(999L))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("应返回详情及关联分类ID列表")
        void shouldReturnDetailWithCategoryIds() {
            // Given
            Brand brand = buildBrand(1L, "小米", 1, "X", 0);
            when(brandMapper.selectById(1L)).thenReturn(brand);
            CategoryBrandRelation rel = new CategoryBrandRelation();
            rel.setBrandId(1L);
            rel.setCatelogId(225L);
            when(categoryBrandRelationMapper.selectList(any())).thenReturn(List.of(rel));

            // When
            BrandVO vo = brandService.getBrandDetail(1L);

            // Then
            assertThat(vo.getName()).isEqualTo("小米");
            assertThat(vo.getCategoryIds()).containsExactly(225L);
        }
    }

    // ==================== 新增品牌 ====================

    @Nested
    @DisplayName("新增品牌")
    class SaveBrand {

        @Test
        @DisplayName("品牌名重复时应抛异常")
        void shouldThrowWhenNameDuplicate() {
            // Given
            BrandSaveDTO dto = buildSaveDTO("小米", null);
            when(brandMapper.selectCount(any())).thenReturn(1L);

            // When & Then
            assertThatThrownBy(() -> brandService.saveBrand(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_NAME_DUPLICATE.getCode());
            verify(brandMapper, never()).insert(any(Brand.class));
        }

        @Test
        @DisplayName("无关联分类时应成功且默认显示状态为 1")
        void shouldSaveWithoutRelations() {
            // Given
            BrandSaveDTO dto = buildSaveDTO("新品牌", null);
            when(brandMapper.selectCount(any())).thenReturn(0L);
            when(brandMapper.insert(any(Brand.class))).thenReturn(1);

            // When & Then
            assertThatCode(() -> brandService.saveBrand(dto)).doesNotThrowAnyException();
            verify(brandMapper).insert(argThat((Brand b) -> b.getShowStatus() == 1));
            verify(categoryBrandRelationMapper, never()).insert(any(CategoryBrandRelation.class));
        }

        @Test
        @DisplayName("关联分类非三级时应抛异常")
        void shouldThrowWhenCategoryNotLevel3() {
            // Given
            BrandSaveDTO dto = buildSaveDTO("新品牌", List.of(225L));
            when(brandMapper.selectCount(any())).thenReturn(0L);
            when(brandMapper.insert(any(Brand.class))).thenReturn(1);
            // 返回一个二级分类
            Category cat = buildCategory(225L, "二级分类", 2);
            when(categoryMapper.selectByIds(anyList())).thenReturn(List.of(cat));

            // When & Then
            assertThatThrownBy(() -> brandService.saveBrand(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_CATEGORY_INVALID.getCode());
        }

        @Test
        @DisplayName("关联三级分类时应成功写入关联")
        void shouldSaveWithValidRelations() {
            // Given
            BrandSaveDTO dto = buildSaveDTO("新品牌", List.of(225L));
            when(brandMapper.selectCount(any())).thenReturn(0L);
            when(brandMapper.insert(any(Brand.class))).thenAnswer(inv -> {
                ((Brand) inv.getArgument(0)).setId(1L);
                return 1;
            });
            when(categoryMapper.selectByIds(anyList())).thenReturn(List.of(buildCategory(225L, "手机", 3)));
            when(categoryBrandRelationMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When & Then
            assertThatCode(() -> brandService.saveBrand(dto)).doesNotThrowAnyException();
            verify(categoryBrandRelationMapper).insert(any(CategoryBrandRelation.class));
        }
    }

    // ==================== 修改品牌 ====================

    @Nested
    @DisplayName("修改品牌")
    class UpdateBrand {

        @Test
        @DisplayName("品牌不存在时应抛异常")
        void shouldThrowWhenNotFound() {
            // Given
            BrandSaveDTO dto = buildSaveDTO("新名称", null);
            dto.setId(999L);
            dto.setVersion(0);
            when(brandMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> brandService.updateBrand(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("改名后名称与他人重复时应抛异常")
        void shouldThrowWhenNameDuplicateOnUpdate() {
            // Given
            BrandSaveDTO dto = buildSaveDTO("华为", null);
            dto.setId(1L);
            dto.setVersion(0);
            Brand existing = buildBrand(1L, "小米", 1, "X", 0);
            when(brandMapper.selectById(1L)).thenReturn(existing);
            when(brandMapper.selectCount(any())).thenReturn(1L);

            // When & Then
            assertThatThrownBy(() -> brandService.updateBrand(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_NAME_DUPLICATE.getCode());
        }

        @Test
        @DisplayName("改名时应同步刷新关联表冗余品牌名")
        void shouldSyncRelationBrandNameWhenRenamed() {
            // Given
            BrandSaveDTO dto = buildSaveDTO("新小米", null);
            dto.setId(1L);
            dto.setVersion(0);
            Brand existing = buildBrand(1L, "小米", 1, "X", 0);
            when(brandMapper.selectById(1L)).thenReturn(existing);
            when(brandMapper.updateById(any(Brand.class))).thenReturn(1);

            // When
            brandService.updateBrand(dto);

            // Then: 触发关联表 brand_name 更新
            verify(categoryBrandRelationMapper).update(isNull(), any());
        }

        @Test
        @DisplayName("传入 categoryIds 时应全量覆盖关联")
        void shouldOverwriteRelationsWhenCategoryIdsProvided() {
            // Given
            BrandSaveDTO dto = buildSaveDTO("小米", List.of(226L));
            dto.setId(1L);
            dto.setVersion(0);
            Brand existing = buildBrand(1L, "小米", 1, "X", 0);
            when(brandMapper.selectById(1L)).thenReturn(existing);
            when(brandMapper.updateById(any(Brand.class))).thenReturn(1);
            when(categoryMapper.selectByIds(anyList())).thenReturn(List.of(buildCategory(226L, "家电", 3)));
            // 现有关联：225（将被删除，因不在期望列表）
            CategoryBrandRelation oldRel = new CategoryBrandRelation();
            oldRel.setCatelogId(225L);
            when(categoryBrandRelationMapper.selectList(any())).thenReturn(List.of(oldRel));

            // When
            brandService.updateBrand(dto);

            // Then: 删除 225，新增 226
            verify(categoryBrandRelationMapper).delete(any());
            verify(categoryBrandRelationMapper).insert(any(CategoryBrandRelation.class));
        }
    }

    // ==================== 更新显示状态 ====================

    @Nested
    @DisplayName("更新显示状态")
    class UpdateShowStatus {

        @Test
        @DisplayName("品牌不存在时应抛异常")
        void shouldThrowWhenNotFound() {
            // Given
            when(brandMapper.selectById(999L)).thenReturn(null);

            // When & Then
            BrandShowStatusDTO dto = new BrandShowStatusDTO();
            dto.setShowStatus(0);
            assertThatThrownBy(() -> brandService.updateShowStatus(999L, dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("应携带乐观锁版本号更新显示状态")
        void shouldUpdateWithVersion() {
            // Given
            Brand existing = buildBrand(1L, "小米", 1, "X", 0);
            existing.setVersion(3);
            when(brandMapper.selectById(1L)).thenReturn(existing);
            when(brandMapper.updateById(any(Brand.class))).thenReturn(1);

            // When
            BrandShowStatusDTO dto = new BrandShowStatusDTO();
            dto.setShowStatus(0);
            brandService.updateShowStatus(1L, dto);

            // Then
            verify(brandMapper).updateById(argThat((Brand b) ->
                    b.getShowStatus() == 0 && b.getVersion() == 3));
        }
    }

    // ==================== 删除品牌 ====================

    @Nested
    @DisplayName("删除品牌")
    class RemoveBrand {

        @Test
        @DisplayName("品牌不存在时应抛异常")
        void shouldThrowWhenNotFound() {
            // Given
            when(brandMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> brandService.removeBrand(999L))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("存在关联商品时应拒绝删除")
        void shouldRejectWhenHasProducts() {
            // Given
            Brand brand = buildBrand(1L, "小米", 1, "X", 0);
            when(brandMapper.selectById(1L)).thenReturn(brand);
            when(spuInfoMapper.selectCount(any())).thenReturn(2L);

            // When & Then
            assertThatThrownBy(() -> brandService.removeBrand(1L))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_HAS_PRODUCTS.getCode());
            verify(brandMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("无关联商品时应逻辑删除品牌及关联")
        void shouldRemoveBrandAndRelations() {
            // Given
            Brand brand = buildBrand(1L, "小米", 1, "X", 0);
            when(brandMapper.selectById(1L)).thenReturn(brand);
            when(spuInfoMapper.selectCount(any())).thenReturn(0L);
            when(brandMapper.deleteById(anyLong())).thenReturn(1);

            // When & Then
            assertThatCode(() -> brandService.removeBrand(1L)).doesNotThrowAnyException();
            verify(brandMapper).deleteById(1L);
            verify(categoryBrandRelationMapper).delete(any());
        }
    }

    // ==================== 分类下品牌 ====================

    @Nested
    @DisplayName("查询分类下品牌")
    class ListByCategory {

        @Test
        @DisplayName("无关联时应返回空列表")
        void shouldReturnEmptyWhenNoRelation() {
            // Given
            when(categoryBrandRelationMapper.selectList(any())).thenReturn(Collections.emptyList());

            // When
            List<BrandSimpleVO> result = brandService.listByCategory(225L);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应返回分类下显示中的品牌")
        void shouldReturnBrandsForCategory() {
            // Given
            CategoryBrandRelation rel = new CategoryBrandRelation();
            rel.setBrandId(1L);
            when(categoryBrandRelationMapper.selectList(any())).thenReturn(List.of(rel));
            Brand brand = buildBrand(1L, "小米", 1, "X", 0);
            when(brandMapper.selectList(any())).thenReturn(List.of(brand));

            // When
            List<BrandSimpleVO> result = brandService.listByCategory(225L);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("小米");
        }
    }

    // ==================== 辅助方法 ====================

    private Brand buildBrand(Long id, String name, int showStatus, String firstLetter, int sort) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setName(name);
        brand.setShowStatus(showStatus);
        brand.setFirstLetter(firstLetter);
        brand.setSort(sort);
        return brand;
    }

    private Category buildCategory(Long id, String name, int catLevel) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setCatLevel(catLevel);
        return category;
    }

    private BrandSaveDTO buildSaveDTO(String name, List<Long> categoryIds) {
        BrandSaveDTO dto = new BrandSaveDTO();
        dto.setName(name);
        dto.setLogo("https://oss.example.com/brand/logo.png");
        dto.setFirstLetter("X");
        dto.setSort(0);
        dto.setCategoryIds(categoryIds);
        return dto;
    }
}
