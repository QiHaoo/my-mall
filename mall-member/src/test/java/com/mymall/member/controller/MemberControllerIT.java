package com.mymall.member.controller;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MemberController 集成测试
 *
 * <p>使用 WireMock 模拟 mall-coupon 服务，验证完整的 Feign 调用链路：
 * Controller → FeignClient → HTTP 请求 → WireMock → 响应反序列化 → Controller 返回
 *
 * <p>与单元测试的区别：不 mock FeignClient Bean，而是用 WireMock 在 HTTP 层拦截，
 * 能验证序列化/反序列化、URL 路由、负载均衡配置等真实行为。
 *
 * <p>使用 WireMockExtension 编程式注册（而非 @WireMockTest 注解），
 * 以便在 @DynamicPropertySource 中读取 WireMock 随机端口。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // 测试环境禁用外部依赖
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.loadbalancer.enabled=false",
        // 不连接真实数据库（@SpringBootTest + H2 自动替换为内存库）
        "spring.sql.init.mode=never",
        // 禁用 Sentinel（测试不需要）
        "spring.cloud.sentinel.enabled=false"
    }
)
@AutoConfigureMockMvc
@DisplayName("MemberController 集成测试 — Feign 真实链路")
class MemberControllerIT {

    /** 编程式注册 WireMockExtension，直接通过扩展实例获取端口 */
    @RegisterExtension
    static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .configureStaticDsl(true)
            .build();

    /**
     * 将 Feign 要调用的目标 URL 指向 WireMock 的随机端口，
     * 绕过 Nacos 服务发现和 LoadBalancer。
     */
    @DynamicPropertySource
    static void configureFeignUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.mall-coupon.url",
                () -> "http://localhost:" + wireMockExtension.getPort());
    }

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("GET /member/member/test-remote → Feign → WireMock(coupon)")
    class TestRemoteIntegration {

        @BeforeEach
        void setUpWireMockStub() {
            // 模拟 coupon 服务 /coupon/coupon/list 返回优惠券列表
            WireMock.stubFor(
                    WireMock.get(urlEqualTo("/coupon/coupon/list"))
                            .willReturn(aResponse()
                                    .withHeader("Content-Type", "application/json")
                                    .withBody("""
                                            {
                                                "code": 200,
                                                "msg": "success",
                                                "data": {
                                                    "coupons": [
                                                        {"id": 1, "couponName": "满100减20"},
                                                        {"id": 2, "couponName": "新人8折券"}
                                                    ],
                                                    "total": 2
                                                }
                                            }
                                            """)));
        }

        @Test
        @DisplayName("应通过 Feign 真实调用 WireMock 并返回完整数据")
        void shouldCallWireMockAndReturnCoupons() throws Exception {
            mockMvc.perform(get("/member/member/test-remote"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.coupons[0].couponName").value("满100减20"))
                    .andExpect(jsonPath("$.data.coupons[1].couponName").value("新人8折券"))
                    .andExpect(jsonPath("$.data.total").value(2));

            // 验证 Feign 确实发出了 HTTP 请求到 WireMock
            WireMock.verify(getRequestedFor(urlEqualTo("/coupon/coupon/list")));
        }
    }
}
