package com.mymall.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mymall.common.exception.BizException;
import com.mymall.common.exception.ResultCode;
import com.mymall.common.result.PageVO;
import com.mymall.product.dto.brand.BrandBatchDeleteDTO;
import com.mymall.product.dto.brand.BrandQueryDTO;
import com.mymall.product.dto.brand.BrandSaveDTO;
import com.mymall.product.dto.brand.BrandShowStatusDTO;
import com.mymall.product.dto.brand.BrandSimpleVO;
import com.mymall.product.dto.brand.BrandVO;
import com.mymall.product.entity.Brand;
import com.mymall.product.entity.CategoryBrandRelation;
import com.mymall.product.mapper.BrandMapper;
import com.mymall.product.mapper.CategoryBrandRelationMapper;
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
    private ICategoryBrandRelationService categoryBrandRelationService;

    private BrandServiceImpl brandService;

    @BeforeEach
    void setUp() {
        // ServiceImpl.baseMapper 由 Spring 字段注入，@InjectMocks 仅走构造器，
        // 父类 baseMapper 字段保持 null，需反射注入。
        brandService = new BrandServiceImpl(categoryBrandRelationMapper, spuInfoMapper, categoryBrandRelationService);
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
        @DisplayName("应返回品牌基础详情")
        void shouldReturnDetailWithoutCategoryIds() {
            // Given
            Brand brand = buildBrand(1L, "小米", 1, "X", 0);
            when(brandMapper.selectById(1L)).thenReturn(brand);

            // When
            BrandVO vo = brandService.getBrandDetail(1L);

            // Then
            assertThat(vo.getName()).isEqualTo("小米");
            assertThat(vo.getFirstLetter()).isEqualTo("X");
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
            BrandSaveDTO dto = buildSaveDTO("小米");
            when(brandMapper.selectCount(any())).thenReturn(1L);

            // When & Then
            assertThatThrownBy(() -> brandService.saveBrand(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_NAME_DUPLICATE.getCode());
            verify(brandMapper, never()).insert(any(Brand.class));
        }

        @Test
        @DisplayName("首字母应转大写且默认显示状态为 1")
        void shouldSaveWithUpperCaseLetterAndDefaultShowStatus() {
            // Given
            BrandSaveDTO dto = buildSaveDTO("新品牌");
            dto.setFirstLetter("x");
            dto.setShowStatus(null);
            when(brandMapper.selectCount(any())).thenReturn(0L);
            when(brandMapper.insert(any(Brand.class))).thenReturn(1);

            // When & Then
            assertThatCode(() -> brandService.saveBrand(dto)).doesNotThrowAnyException();
            verify(brandMapper).insert(argThat((Brand b) ->
                    b.getShowStatus() == 1 && "X".equals(b.getFirstLetter())));
            verify(categoryBrandRelationMapper, never()).insert(any(CategoryBrandRelation.class));
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
            BrandSaveDTO dto = buildSaveDTO("新名称");
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
            BrandSaveDTO dto = buildSaveDTO("华为");
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
            BrandSaveDTO dto = buildSaveDTO("新小米");
            dto.setId(1L);
            dto.setVersion(0);
            Brand existing = buildBrand(1L, "小米", 1, "X", 0);
            when(brandMapper.selectById(1L)).thenReturn(existing);
            when(brandMapper.updateById(any(Brand.class))).thenReturn(1);

            // When
            brandService.updateBrand(dto);

            // Then: 触发关联表 brand_name 更新
            verify(categoryBrandRelationService).updateBrandName(1L, "新小米");
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

    // ==================== 批量删除品牌 ====================

    @Nested
    @DisplayName("批量删除品牌")
    class BatchDelete {

        @Test
        @DisplayName("id 列表为空时应抛异常")
        void shouldThrowWhenIdsEmpty() {
            // Given
            BrandBatchDeleteDTO dto = new BrandBatchDeleteDTO();
            dto.setIds(Collections.emptyList());

            // When & Then
            assertThatThrownBy(() -> brandService.batchDelete(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_BATCH_DELETE_EMPTY.getCode());
        }

        @Test
        @DisplayName("任一品牌存在商品引用时整体回滚")
        void shouldRollbackWhenAnyBrandHasProducts() {
            // Given
            Brand brand1 = buildBrand(1L, "小米", 1, "X", 0);
            Brand brand2 = buildBrand(2L, "华为", 1, "H", 0);
            BrandBatchDeleteDTO dto = new BrandBatchDeleteDTO();
            dto.setIds(List.of(1L, 2L));
            when(brandMapper.selectById(1L)).thenReturn(brand1);
            when(brandMapper.selectById(2L)).thenReturn(brand2);
            when(spuInfoMapper.selectCount(any())).thenReturn(0L, 1L);

            // When & Then
            assertThatThrownBy(() -> brandService.batchDelete(dto))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BRAND_HAS_PRODUCTS.getCode());
            verify(brandMapper, never()).deleteByIds(anyList());
        }

        @Test
        @DisplayName("全部无引用时应统一删除")
        void shouldBatchDeleteWhenNoReferences() {
            // Given
            Brand brand1 = buildBrand(1L, "小米", 1, "X", 0);
            Brand brand2 = buildBrand(2L, "华为", 1, "H", 0);
            BrandBatchDeleteDTO dto = new BrandBatchDeleteDTO();
            dto.setIds(List.of(1L, 2L));
            when(brandMapper.selectById(1L)).thenReturn(brand1);
            when(brandMapper.selectById(2L)).thenReturn(brand2);
            when(spuInfoMapper.selectCount(any())).thenReturn(0L);
            when(brandMapper.deleteByIds(anyList())).thenReturn(2);

            // When & Then
            assertThatCode(() -> brandService.batchDelete(dto)).doesNotThrowAnyException();
            verify(brandMapper).deleteByIds(List.of(1L, 2L));
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

    private BrandSaveDTO buildSaveDTO(String name) {
        BrandSaveDTO dto = new BrandSaveDTO();
        dto.setName(name);
        dto.setLogo("https://oss.example.com/brand/logo.png");
        dto.setFirstLetter("X");
        dto.setSort(0);
        return dto;
    }
}
