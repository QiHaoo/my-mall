package com.mymall.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mymall.common.exception.BizException;
import com.mymall.common.exception.GlobalExceptionHandler;
import com.mymall.common.exception.ResultCode;
import com.mymall.product.dto.category.CategoryVO;
import com.mymall.product.service.ICategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CategoryController 切片测试
 * <p>
 * 验证 HTTP 层行为：路由、参数校验、响应序列化、异常处理。
 */
@WebMvcTest(CategoryController.class)
@DisplayName("商品分类 Controller")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ICategoryService categoryService;

    // ==================== 分类树查询 ====================

    @Nested
    @DisplayName("GET /product/category/tree")
    class Tree {

        @Test
        @DisplayName("应返回 200 和空分类树")
        void shouldReturnEmptyTree() throws Exception {
            // Given
            when(categoryService.listTree()).thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/product/category/tree"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("应返回分类树数据")
        void shouldReturnTreeData() throws Exception {
            // Given
            CategoryVO vo = new CategoryVO();
            vo.setCatId(1L);
            vo.setName("图书");
            vo.setParentCid(0L);
            vo.setCatLevel(1);
            vo.setShowStatus(1);
            vo.setSort(0);
            vo.setChildren(Collections.emptyList());
            when(categoryService.listTree()).thenReturn(List.of(vo));

            // When & Then
            mockMvc.perform(get("/product/category/tree"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].catId").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("图书"));
        }
    }

    // ==================== 新增分类 ====================

    @Nested
    @DisplayName("POST /product/category")
    class Save {

        @Test
        @DisplayName("合法参数应返回 200")
        void shouldReturn200WithValidParams() throws Exception {
            // Given
            String body = """
                    {
                        "name": "手机通讯",
                        "parentCid": 0,
                        "sort": 1
                    }
                    """;

            // When & Then
            mockMvc.perform(post("/product/category")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(categoryService).saveCategory(any());
        }

        @Test
        @DisplayName("名称为空时应返回参数错误")
        void shouldReturn400WhenNameEmpty() throws Exception {
            // Given
            String body = """
                    {
                        "name": "",
                        "parentCid": 0
                    }
                    """;

            // When & Then
            mockMvc.perform(post("/product/category")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("parentCid 为 null 时应返回参数错误")
        void shouldReturn400WhenParentCidNull() throws Exception {
            // Given
            String body = """
                    {
                        "name": "测试分类"
                    }
                    """;

            // When & Then
            mockMvc.perform(post("/product/category")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 修改分类 ====================

    @Nested
    @DisplayName("PUT /product/category")
    class Update {

        @Test
        @DisplayName("合法参数应返回 200")
        void shouldReturn200WithValidParams() throws Exception {
            // Given
            String body = """
                    {
                        "catId": 1,
                        "name": "新名称"
                    }
                    """;

            // When & Then
            mockMvc.perform(put("/product/category")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(categoryService).updateCategory(any());
        }

        @Test
        @DisplayName("catId 为 null 时应返回参数错误")
        void shouldReturn400WhenCatIdNull() throws Exception {
            // Given
            String body = """
                    {
                        "name": "新名称"
                    }
                    """;

            // When & Then
            mockMvc.perform(put("/product/category")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== 批量删除 ====================

    @Nested
    @DisplayName("POST /product/category/batch-delete")
    class BatchDelete {

        @Test
        @DisplayName("合法ID列表应返回 200")
        void shouldReturn200WithValidIds() throws Exception {
            // Given
            String body = """
                    {
                        "ids": [1, 2, 3]
                    }
                    """;

            // When & Then
            mockMvc.perform(post("/product/category/batch-delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(categoryService).batchDelete(anyList());
        }

        @Test
        @DisplayName("空ID列表应返回参数错误")
        void shouldReturn400WhenEmptyIds() throws Exception {
            // Given
            String body = """
                    {
                        "ids": []
                    }
                    """;

            // When & Then
            mockMvc.perform(post("/product/category/batch-delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("删除一级分类应返回业务异常")
        void shouldReturnBizErrorWhenDeleteRoot() throws Exception {
            // Given
            String body = """
                    {
                        "ids": [1]
                    }
                    """;
            doThrow(new BizException(ResultCode.CATEGORY_ROOT_DELETE))
                    .when(categoryService).batchDelete(anyList());

            // When & Then
            mockMvc.perform(post("/product/category/batch-delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(51007));
        }
    }

    // ==================== 拖拽排序 ====================

    @Nested
    @DisplayName("PUT /product/category/sort")
    class Sort {

        @Test
        @DisplayName("合法排序请求应返回 200")
        void shouldReturn200WithValidSort() throws Exception {
            // Given
            String body = """
                    {
                        "categories": [
                            {"catId": 5, "parentCid": 2, "catLevel": 2, "sort": 1},
                            {"catId": 6, "parentCid": 2, "catLevel": 2, "sort": 2}
                        ]
                    }
                    """;

            // When & Then
            mockMvc.perform(put("/product/category/sort")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(categoryService).sortCategories(any());
        }

        @Test
        @DisplayName("空排序列表应返回参数错误")
        void shouldReturn400WhenEmptyCategories() throws Exception {
            // Given
            String body = """
                    {
                        "categories": []
                    }
                    """;

            // When & Then
            mockMvc.perform(put("/product/category/sort")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}
