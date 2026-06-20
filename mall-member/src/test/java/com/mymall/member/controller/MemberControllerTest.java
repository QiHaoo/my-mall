package com.mymall.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mymall.common.result.R;
import com.mymall.member.feign.CouponFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MemberController 单元测试
 *
 * <p>使用 @WebMvcTest 只加载 Controller 层，不启动整个 Spring 上下文，执行速度快。
 * Feign 客户端用 Mockito mock，避免发起真实的远程网络调用。
 */
@WebMvcTest(MemberController.class)
@DisplayName("MemberController 远程调用测试")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Mock Feign 客户端，隔离远程依赖 */
    @MockitoBean
    private CouponFeignClient couponFeignClient;

    @Nested
    @DisplayName("GET /member/member/test-remote")
    class TestRemote {

        private R mockResponse;

        @BeforeEach
        void setUp() {
            // 构造 mock 返回数据，模拟 coupon 服务的响应
            Map<String, Object> coupon = Map.of("id", 1, "couponName", "满100减20");
            mockResponse = R.ok().put("coupons", List.of(coupon)).put("total", 1);
        }

        @Test
        @DisplayName("应返回 200 并透传远程服务的数据")
        void shouldReturnRemoteCouponList() throws Exception {
            when(couponFeignClient.list()).thenReturn(mockResponse);

            // 注意：R 的 data 是 Map，Jackson 序列化后 key 在 data 嵌套内
            mockMvc.perform(get("/member/member/test-remote"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.coupons").isArray())
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("当远程服务返回空列表时应正常返回")
        void  shouldReturnEmptyListWhenNoCoupons() throws Exception {
            R emptyResponse = R.ok().put("coupons", List.of()).put("total", 0);
            when(couponFeignClient.list()).thenReturn(emptyResponse);

            mockMvc.perform(get("/member/member/test-remote"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.coupons").isEmpty())
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }
}
