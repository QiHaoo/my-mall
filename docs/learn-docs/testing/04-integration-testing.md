# 04 · 集成测试

> 集成测试验证跨层/跨服务的完整链路。本篇讲 WireMock 怎么模拟远程服务，以及集成测试和单元/切片测试的本质区别。
>
> 前置阅读：[03-slice-testing.md](./03-slice-testing.md)

---

## 一、集成测试解决什么问题

假设 mall-member 服务通过 Feign 调用 mall-coupon 服务：

```
浏览器 → mall-member /member/test-remote → Feign → mall-coupon /coupon/list
```

这条链路，前两篇的测试都测不全：

| 测试类型 | 能测到哪 | 测不到什么 |
|---------|---------|-----------|
| 单元测试（Service） | Service 业务逻辑 | Feign 序列化、HTTP 传输、反序列化 |
| 切片测试（Controller） | Controller → Service | Feign 调用（Controller 测试里 Service 被 Mock 了） |

**集成测试就是启动完整的 Spring 上下文，让 Feign 真的发 HTTP 请求**——但请求发到一个「假的 HTTP 服务器」（WireMock），而不是真实的 mall-coupon 服务。

这样就能验证整条链路：

```
Controller → Service → FeignClient → 真实 HTTP 请求 → WireMock（拦截，返回假数据）
                                                              ↑
                                          不经过 Nacos / LoadBalancer
```

验证点包括：
- Feign 的 URL 拼接对不对
- 请求参数序列化成 JSON 对不对
- 响应 JSON 反序列化成 Java 对象对不对
- `R<T>` 的泛型解析对不对
- Feign 的拦截器（如鉴权头透传）工不工作

---

## 二、WireMock 是什么

WireMock 是一个「可编程的 HTTP Mock 服务器」。你告诉它：

> 「如果收到 `GET /coupon/list` 请求，就返回这段 JSON」

它就在本地启动一个 HTTP 服务，按你的设定响应。Feign 发出的真实 HTTP 请求会被 WireMock 拦截并返回预设响应。

```
代码里写：
  stubFor(get("/coupon/list").willReturn(okJson("{...}")));

WireMock 在本地启动（比如端口 8089）：
  收到 GET /coupon/list → 返回你设定的 JSON
  收到其他请求 → 返回 404

Feign 的 URL 被指向 WireMock：
  feign → http://localhost:8089/coupon/list → WireMock 响应
```

### 2.1 WireMock 的核心 API

```java
// 打桩：设定「什么请求 → 返回什么响应」
stubFor(get(urlEqualTo("/coupon/list"))                    // 匹配 GET /coupon/list
        .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":200,\"data\":[...]}")));

// 验证：检查「某个请求是否真的发出来了」
verify(getRequestedFor(urlEqualTo("/coupon/list")));       // 确认 Feign 发了这个请求
verify(getRequestedFor(urlEqualTo("/coupon/list")), times(2));  // 确认发了 2 次
```

| API | 作用 |
|-----|------|
| `stubFor(...)` | 打桩——设定请求和响应的映射 |
| `get/post/put/delete(url)` | 匹配 HTTP 方法和 URL |
| `aResponse().withBody(...)` | 设定响应内容 |
| `verify(getRequestedFor(url))` | 验证某个请求是否发出 |

> WireMock 的 `stubFor`/`verify` 和 Mockito 的 `when`/`verify` 思路一样——一个是 Mock HTTP 请求，一个是 Mock 方法调用。

---

## 三、集成测试怎么写

### 3.1 核心注解和配置

```java
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.cloud.nacos.discovery.enabled=false",   // 禁用 Nacos 服务发现
    "spring.cloud.nacos.config.enabled=false",      // 禁用 Nacos 配置中心
    "spring.cloud.loadbalancer.enabled=false"        // 禁用负载均衡
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberControllerIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())  // 随机端口
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void configureFeignUrl(DynamicPropertyRegistry registry) {
        // 把 Feign 的 URL 指向 WireMock，绕过 Nacos 和 LoadBalancer
        registry.add("spring.cloud.openfeign.client.config.mall-coupon.url",
                () -> "http://localhost:" + wireMock.getPort());
    }

    @Autowired
    private MockMvc mockMvc;

    // ... 测试方法
}
```

### 3.2 逐行解释关键配置

**① `@SpringBootTest(webEnvironment = RANDOM_PORT)`**

启动完整的 Spring 上下文，和单元/切片测试的区别：

| 注解 | 启动范围 | 速度 |
|------|---------|------|
| `@ExtendWith(MockitoExtension.class)` | 不启动 Spring | <1s |
| `@WebMvcTest` | 只启动 MVC 层 | ~2s |
| `@SpringBootTest` | 启动完整上下文（所有 Bean） | ~5s |

集成测试需要 Feign 真实工作，所以要完整上下文。

**② 禁用 Nacos 和 LoadBalancer**

```java
properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.loadbalancer.enabled=false"
}
```

为什么不连 Nacos？因为：
- 测试环境没有 Nacos 服务（或者不想依赖它）
- Feign 默认通过 Nacos 找到 mall-coupon 的地址，但我们想让 Feign 直接指向 WireMock
- 禁用 LoadBalancer，让 Feign 不做负载均衡，直接用配置的 URL

**③ `@RegisterExtension` + WireMockExtension**

```java
@RegisterExtension
static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().dynamicPort())  // 随机端口
        .configureStaticDsl(true)
        .build();
```

- `dynamicPort()`：WireMock 启动时随机选一个空闲端口，避免端口冲突
- `configureStaticDsl(true)`：允许用静态方法 `stubFor()`/`verify()`（否则要用实例方法）
- `@RegisterExtension`：JUnit 5 的扩展，测试类启动时自动启动 WireMock，结束时自动停止

**④ `@DynamicPropertySource` 把 Feign 指向 WireMock**

```java
@DynamicPropertySource
static void configureFeignUrl(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.openfeign.client.config.mall-coupon.url",
            () -> "http://localhost:" + wireMock.getPort());
}
```

这是最关键的一步——告诉 Feign：「别去 Nacos 找 mall-coupon 了，直接请求 WireMock 的地址」。

`spring.cloud.openfeign.client.config.mall-coupon.url` 这个配置项是 OpenFeign 的，`mall-coupon` 是 `@FeignClient(name = "mall-coupon")` 里的名字。配置后，Feign 调用 mall-coupon 的接口时直接请求这个 URL，不走服务发现。

---

## 四、完整实战

参照项目的 [MemberControllerIT.java](https://github.com/QiHaoo/my-mall/blob/main/mall-member/src/test/java/com/mymall/member/controller/MemberControllerIT.java)：

```java
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.loadbalancer.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberControllerIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .configureStaticDsl(true)
            .build();

    @DynamicPropertySource
    static void configureFeignUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.mall-coupon.url",
                () -> "http://localhost:" + wireMock.getPort());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("应通过 Feign 真实调用 WireMock 并返回完整数据")
    void shouldCallWireMockAndReturnData() throws Exception {
        // Given: 让 WireMock 在收到 /coupon/coupon/list 时返回这段 JSON
        WireMock.stubFor(WireMock.get(urlEqualTo("/coupon/coupon/list"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":200,"data":{"coupons":[{"id":1,"couponName":"满100减20"}],"total":1}}
                                """)));

        // When & Then: 调 member 接口，它内部会通过 Feign 调 coupon
        mockMvc.perform(get("/member/member/test-remote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coupons").isArray())
                .andExpect(jsonPath("$.data.total").value(1));

        // Verify: 确认 Feign 确实发出了 HTTP 请求到 WireMock
        WireMock.verify(getRequestedFor(urlEqualTo("/coupon/coupon/list")));
    }
}
```

### 这个测试验证了什么

```
mockMvc 发请求
  → MemberController.testRemote()
    → MemberService 调用 CouponFeignClient.list()
      → Feign 真实发起 HTTP GET 请求
        → WireMock 拦截，返回预设 JSON
      ← Feign 把 JSON 反序列化成 R<Map>
    ← Service 处理后返回
  ← Controller 序列化成响应 JSON
← mockMvc 断言响应

最后 verify：确认 Feign 确实请求了 /coupon/coupon/list
```

整条链路里如果有任何一环出错（URL 拼错、序列化格式错、泛型解析错），测试都会挂。这就是集成测试的价值——**验证「组装」是否正确，而单元测试只能验证「零件」是否正确**。

---

## 五、集成测试 vs 切片测试 vs E2E

现在把三层测试放一起对比，彻底理清区别：

| 维度 | 单元测试 | 切片测试 | 集成测试 | E2E 测试 |
|------|---------|---------|---------|---------|
| 注解 | 无 | `@WebMvcTest` | `@SpringBootTest` | `@SpringBootTest` |
| Spring 上下文 | 不启动 | 部分启动 | 完整启动 | 完整启动 |
| Service | `@Mock` 假的 | `@MockitoBean` 假的 | **真实的** | 真实的 |
| 数据库 | 不涉及 | 不涉及 | H2 内存库 / Mock | **真实 MySQL** |
| 远程服务 | `@Mock` 假的 | 不涉及 | **WireMock 假的** | **真实服务** |
| 验证重点 | 业务逻辑 | HTTP 层行为 | 跨层组装 | 真实环境 |
| 速度 | <1s | ~2s | ~5s | 10~30s |
| 类后缀 | `*Test` | `*Test` | `*IT` | `*E2ET` |
| 执行插件 | surefire | surefire | failsafe | failsafe |

**关键区别——集成测试 vs E2E**：

```
集成测试（WireMock）：
  member 服务 → Feign → WireMock（假的 coupon 服务）
  数据库：H2 内存库（假的 MySQL）
  特点：快、稳定，但不验证真实服务的行为

E2E 测试（Testcontainers）：
  member 服务 → Feign → 真实的 coupon 服务 → 真实 MySQL
  特点：慢、重，但最接近生产环境
```

集成测试验证「我的代码组装得对不对」，E2E 验证「整个系统在真实环境下能不能跑」。本项目当前用集成测试（WireMock），E2E 测试规划在后期关键链路使用（见 [e2e-testing-strategies.md](./e2e-testing-strategies.md)）。

---

## 六、为什么集成测试用 *IT 后缀

集成测试类命名用 `*IT` 后缀（如 `MemberControllerIT`），不是 `*Test`。这关系到由哪个 Maven 插件执行：

| 后缀 | 执行插件 | Maven 命令 | 阶段 |
|------|---------|-----------|------|
| `*Test` / `*Tests` | maven-surefire-plugin | `mvn test` | test |
| `*IT` / `*ITCase` | maven-failsafe-plugin | `mvn verify` | integration-test |

为什么要分开？

- **surefire（test 阶段）**：跑单元测试和切片测试，要求**快**。失败立即停止（快速失败）。
- **failsafe（integration-test 阶段）**：跑集成测试，**较慢**。允许重试，尽量跑完收集所有失败。

所以 `mvn test` 只跑快的单元/切片测试（几秒），`mvn verify` 才会额外跑集成测试（加几十秒）。

> 详细的 surefire/failsafe 区别和 Maven 生命周期，见 [../ci/03-ci-testing-strategy.md](../ci/03-ci-testing-strategy.md) 第三章。

---

## 七、核心知识点速查

| 知识点 | 一句话 |
|--------|--------|
| 集成测试 | 启动完整 Spring 上下文，验证跨层/跨服务链路组装是否正确 |
| WireMock | 可编程的 HTTP Mock 服务器，拦截 Feign 的真实 HTTP 请求 |
| `stubFor(get(url).willReturn(json))` | 打桩——设定 WireMock 收到什么请求返回什么响应 |
| `verify(getRequestedFor(url))` | 验证——确认 Feign 确实发出了这个 HTTP 请求 |
| `@DynamicPropertySource` | 动态把 Feign URL 指向 WireMock，绕过 Nacos |
| 禁用 Nacos/LoadBalancer | 集成测试不依赖真实服务发现，直接用 WireMock URL |
| `*IT` 后缀 | 由 failsafe 插件在 `mvn verify` 时执行，和 `*Test` 分离 |
| 集成 vs E2E | 集成用 WireMock 假服务 + H2 假库；E2E 用真实服务 + 真实中间件 |

---

## 八、四篇串起来：测试全貌

到这里，测试学习笔记的核心四篇就讲完了。回顾一下整个测试体系：

```
┌──────────────────────────────────────────────┐
│  E2E 测试（Testcontainers，关键链路）          │  真实中间件，验证真实环境
│  *E2ET，failsafe，mvn verify                  │  10~30s
├──────────────────────────────────────────────┤
│  集成测试（WireMock，有 Feign 时写）           │  真实 Feign + 假远程服务
│  *IT，failsafe，mvn verify                    │  ~5s
├──────────────────────────────────────────────┤
│  切片测试（@WebMvcTest，Controller 必须）      │  只启动 MVC 层
│  *Test，surefire，mvn test                    │  ~2s
├──────────────────────────────────────────────┤
│  单元测试（JUnit 5 + Mockito，Service 必须）   │  不启动 Spring，Mock 所有依赖
│  *Test，surefire，mvn test                    │  <1s
└──────────────────────────────────────────────┘
```

**什么时候写哪种**：

| 场景 | 写哪种 |
|------|--------|
| Service 有业务逻辑（分支、异常、边界） | 单元测试 |
| Controller 有 HTTP 路由/参数校验 | 切片测试 |
| 接口通过 Feign 调远程服务 | 集成测试 |
| 下单/秒杀等关键业务全链路 | E2E 测试 |
| Mapper 有自定义复杂 SQL | 切片测试（`@MybatisPlusTest`） |
| `BaseMapper` 单表 CRUD | 不写（框架保证） |
| 纯 DTO/VO/Entity | 不写（无逻辑） |

---

## 下一步

核心四篇已讲完，你现在能看懂 `docs/standards/testing-specification.md` 整套测试设计方案了。延伸阅读：

- [e2e-testing-strategies.md](./e2e-testing-strategies.md) — E2E 三种方案对比（Testcontainers/契约/WireMock）
- [../ci/03-ci-testing-strategy.md](../ci/03-ci-testing-strategy.md) — CI 中的测试、覆盖率门禁、快速失败
- [../development-workflow.md](../../standards/development-workflow.md) — TDD 流程（核心逻辑怎么 RED→GREEN→REFACTOR）
