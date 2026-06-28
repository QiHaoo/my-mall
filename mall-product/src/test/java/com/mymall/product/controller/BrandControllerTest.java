package com.mymall.product.controller;

import com.mymall.common.exception.BizException;
import com.mymall.common.exception.GlobalExceptionHandler;
import com.mymall.common.exception.ResultCode;
import com.mymall.product.dto.brand.BrandSimpleVO;
import com.mymall.product.dto.brand.BrandVO;
import com.mymall.product.service.IBrandService;
import com.mymall.product.service.ICategoryBrandRelationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BrandController 切片测试
 * <p>
 * 验证 HTTP 层行为：路由、参数校验、响应序列化、异常处理。
 * <p>
 * 显式 @Import GlobalExceptionHandler：统一 200 + R.code 策略下，校验异常须由
 * GlobalExceptionHandler 转换为 R 响应体，切片测试需手动引入该 advice。
 */
@WebMvcTest(BrandController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("品牌管理 Controller")
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IBrandService brandService;

    @MockitoBean
    private ICategoryBrandRelationService categoryBrandRelationService;

    // ==================== 分页查询 ====================

    @Nested
    @DisplayName("GET /product/brand")
    class ListBrands {

        @Test
        @DisplayName("合法查询参数应返回 200")
        void shouldReturn200WithValidParams() throws Exception {
            // When & Then
            mockMvc.perform(get("/product/brand").param("pageNum", "1").param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(brandService).pageQuery(any());
        }
    }

    // ==================== 品牌详情 ====================

    @Nested
    @DisplayName("GET /product/brand/{id}")
    class GetById {

        @Test
        @DisplayName("应返回 200 和品牌详情")
        void shouldReturnBrandDetail() throws Exception {
            // Given
            BrandVO vo = new BrandVO();
            vo.setId(1L);
            vo.setName("小米");
            when(brandService.getBrandDetail(1L)).thenReturn(vo);

            // When & Then
            mockMvc.perform(get("/product/brand/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("小米"));
        }

        @Test
        @DisplayName("品牌不存在时应返回业务异常")
        void shouldReturnBizErrorWhenNotFound() throws Exception {
            // Given
            when(brandService.getBrandDetail(999L))
                    .thenThrow(new BizException(ResultCode.BRAND_NOT_FOUND));

            // When & Then
            mockMvc.perform(get("/product/brand/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(53001));
        }
    }

    // ==================== 新增品牌 ====================

    @Nested
    @DisplayName("POST /product/brand")
    class Save {

        @Test
        @DisplayName("合法参数应返回 200")
        void shouldReturn200WithValidParams() throws Exception {
            // Given
            String body = """
                    {
                        "name": "小米",
                        "logo": "https://oss.example.com/brand/mi.png",
                        "firstLetter": "X",
                        "sort": 0
                    }
                    """;

            // When & Then
            mockMvc.perform(post("/product/brand")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(brandService).saveBrand(any());
        }

        @Test
        @DisplayName("品牌名为空时应返回参数错误")
        void shouldReturn400WhenNameEmpty() throws Exception {
            // Given
            String body = """
                    {
                        "name": "",
                        "logo": "https://oss.example.com/brand/mi.png"
                    }
                    """;

            // When & Then
            mockMvc.perform(post("/product/brand")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("logo 为空时应返回参数错误")
        void shouldReturn400WhenLogoEmpty() throws Exception {
            // Given
            String body = """
                    {
                        "name": "小米"
                    }
                    """;

            // When & Then
            mockMvc.perform(post("/product/brand")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("品牌名重复时应返回业务异常")
        void shouldReturnBizErrorWhenNameDuplicate() throws Exception {
            // Given
            String body = """
                    {
                        "name": "小米",
                        "logo": "https://oss.example.com/brand/mi.png"
                    }
                    """;
            doThrow(new BizException(ResultCode.BRAND_NAME_DUPLICATE))
                    .when(brandService).saveBrand(any());

            // When & Then
            mockMvc.perform(post("/product/brand")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(53002));
        }
    }

    // ==================== 修改品牌 ====================

    @Nested
    @DisplayName("PUT /product/brand")
    class Update {

        @Test
        @DisplayName("合法参数应返回 200")
        void shouldReturn200WithValidParams() throws Exception {
            // Given
            String body = """
                    {
                        "id": 1,
                        "name": "小米",
                        "logo": "https://oss.example.com/brand/mi.png",
                        "version": 0
                    }
                    """;

            // When & Then
            mockMvc.perform(put("/product/brand")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(brandService).updateBrand(any());
        }

        @Test
        @DisplayName("id 为 null 时应返回参数错误")
        void shouldReturn400WhenIdNull() throws Exception {
            // Given
            String body = """
                    {
                        "name": "小米",
                        "logo": "https://oss.example.com/brand/mi.png",
                        "version": 0
                    }
                    """;

            // When & Then
            mockMvc.perform(put("/product/brand")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("version 为 null 时应返回参数错误")
        void shouldReturn400WhenVersionNull() throws Exception {
            // Given
            String body = """
                    {
                        "id": 1,
                        "name": "小米",
                        "logo": "https://oss.example.com/brand/mi.png"
                    }
                    """;

            // When & Then
            mockMvc.perform(put("/product/brand")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== 更新显示状态 ====================

    @Nested
    @DisplayName("PUT /product/brand/{id}/show-status")
    class UpdateShowStatus {

        @Test
        @DisplayName("合法状态值应返回 200")
        void shouldReturn200WithValidStatus() throws Exception {
            // Given
            String body = """
                    {
                        "showStatus": 0
                    }
                    """;

            // When & Then
            mockMvc.perform(put("/product/brand/1/show-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(brandService).updateShowStatus(eq(1L), any());
        }

        @Test
        @DisplayName("showStatus 为 null 时应返回参数错误")
        void shouldReturn400WhenStatusNull() throws Exception {
            // Given
            String body = """
                    {
                        "showStatus": null
                    }
                    """;

            // When & Then
            mockMvc.perform(put("/product/brand/1/show-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("showStatus 超出范围时应返回参数错误")
        void shouldReturn400WhenStatusOutOfRange() throws Exception {
            // Given
            String body = """
                    {
                        "showStatus": 2
                    }
                    """;

            // When & Then
            mockMvc.perform(put("/product/brand/1/show-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== 删除品牌 ====================

    @Nested
    @DisplayName("DELETE /product/brand/{id}")
    class Delete {

        @Test
        @DisplayName("合法ID应返回 200")
        void shouldReturn200WithValidId() throws Exception {
            // When & Then
            mockMvc.perform(delete("/product/brand/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(brandService).removeBrand(1L);
        }

        @Test
        @DisplayName("存在关联商品时应返回业务异常")
        void shouldReturnBizErrorWhenHasProducts() throws Exception {
            // Given
            doThrow(new BizException(ResultCode.BRAND_HAS_PRODUCTS))
                    .when(brandService).removeBrand(1L);

            // When & Then
            mockMvc.perform(delete("/product/brand/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(53003));
        }
    }

    // ==================== 分类下品牌 ====================

    @Nested
    @DisplayName("GET /product/brand/by-category/{catelogId}")
    class ListByCategory {

        @Test
        @DisplayName("应返回 200 和品牌列表")
        void shouldReturnBrandList() throws Exception {
            // Given
            BrandSimpleVO vo = new BrandSimpleVO();
            vo.setId(1L);
            vo.setName("小米");
            when(brandService.listByCategory(225L)).thenReturn(List.of(vo));

            // When & Then
            mockMvc.perform(get("/product/brand/by-category/225"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("小米"));
        }

        @Test
        @DisplayName("无品牌时应返回空列表")
        void shouldReturnEmptyList() throws Exception {
            // Given
            when(brandService.listByCategory(225L)).thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/product/brand/by-category/225"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }
}
