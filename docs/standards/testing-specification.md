# 测试规范

> 明确每种接口/逻辑该写什么测试、怎么写，保持全项目风格统一。
> 与 `docs/development-workflow.md`（TDD 流程）配合使用。
>
> **核心原则**：采用业界成熟方案，所学内容可直接迁移到生产环境。
>
> **文档范围**：本文档覆盖后端测试分层、工具链、写法规范、覆盖率门禁、CI 集成、E2E 测试规划，以及前端测试体系的建立规划。其中前端测试体系（第十一章）为待建立项，当前以前端编码规范 `docs/frontend/coding-standards.md` 为准。

---

## 一、测试分层总览

```
              ┌──────────────────────────────────┐
              │           E2E 测试               │  Testcontainers（关键链路必写）
              │  真实中间件 + 多服务联调           │
            ┌─┴──────────────────────────────────┴─┐
            │           集成测试（IT）               │  @SpringBootTest
            │  完整 Spring 上下文 + H2/WireMock     │  验证跨层/跨服务链路
          ┌─┴──────────────────────────────────────┴─┐
          │           切片测试（Slice）               │  @WebMvcTest / @MybatisPlusTest
          │  只加载某一层，其余 Mock                  │  验证单层逻辑
        ┌─┴──────────────────────────────────────────┴─┐
        │           纯单元测试（Unit）                   │  纯 JUnit 5
        │  无 Spring 上下文，Mock 所有依赖               │  验证业务规则
      └────────────────────────────────────────────────┘
```

| 层级 | 测试类型 | 注解 | 速度 | 写不写 |
|------|---------|------|------|--------|
| Service | 纯单元测试 | 无（JUnit 5 + Mockito） | 极快（<1s） | 核心逻辑必须 |
| Controller | 切片测试 | `@WebMvcTest` | 快（~2s） | 必须 |
| Mapper | 切片测试 | `@MybatisPlusTest` | 快（~2s） | 复杂查询写，单表 CRUD 不写 |
| 跨服务链路 | 集成测试 | `@SpringBootTest` + WireMock | 中（~5s） | 有 Feign 调用时写 |
| 全链路 | E2E | `@SpringBootTest` + Testcontainers | 慢（10~30s） | 关键业务链路必写 |
| 公共组件 | 切片/单元 | `@WebMvcTest` / 纯 JUnit | 快 | 横切核心组件必须 |
| 网关过滤器 | 切片 | `WebTestClient` | 快 | 必须 |
| 前端 | 单元/组件 | Vitest + Vue Test Utils | 极快 | 工具函数/通用组件/composables 必须 |

---

## 二、核心工具链

本项目测试基于以下业界标准工具链，与生产环境完全一致：

| 工具 | 作用 | 说明 |
|------|------|------|
| **JUnit 5** | 测试框架 | Spring Boot 3 默认引擎，`@Test` / `@Nested` / `@ParameterizedTest` |
| **Mockito 5** | Mock 框架 | `@Mock` / `@InjectMocks` / `@MockitoBean`（Spring Boot 3.4+ 替代已废弃的 `@MockBean`） |
| **AssertJ** | 断言库 | 流式断言，**本项目统一用 AssertJ 替代 JUnit 原生断言**。Spring Boot Test 自带 |
| **MockMvc** | HTTP 层测试 | `@WebMvcTest` 切片测试中模拟 HTTP 请求 |
| **WireMock** | HTTP 服务 Mock | 集成测试中模拟 Feign 远程调用的目标服务 |
| **H2** | 内存数据库 | 测试中替代 MySQL，`MODE=MySQL` 兼容 MySQL 语法 |
| **Testcontainers** | 真实中间件测试 | E2E 测试中启动真实 MySQL/Redis/RocketMQ 容器，验证全链路 |
| **Jacoco** | 覆盖率统计 | 统计行/分支覆盖率，CI 门禁卡点（新代码 ≥80%） |
| **WebTestClient** | WebFlux 测试客户端 | 网关过滤器切片测试，模拟请求经过过滤器链 |
| **Vitest** | 前端测试框架 | Vite 原生测试框架，极速 HMR，替代 Jest |
| **Vue Test Utils** | Vue 组件测试 | 挂载/交互/快照测试 Vue 组件 |

### 2.1 为什么用 AssertJ 而不是 JUnit 原生断言

```java
// ❌ JUnit 原生断言：可读性差，失败信息不友好
assertEquals(200, result.getCode());
assertNotNull(result.getData());
assertTrue(list.contains(expected));

// ✅ AssertJ：流式 API，类型安全，失败信息丰富
assertThat(result.getCode()).isEqualTo(200);
assertThat(result.getData()).isNotNull();
assertThat(list).contains(expected).hasSize(3);

// ✅ AssertJ 链式断言，一行搞定复合校验
assertThat(coupons)
    .hasSize(2)
    .extracting("couponName")
    .containsExactly("满100减20", "新人8折券");
```

### 2.2 可测试性设计原则

测试好不好写，取决于生产代码的设计。以下原则从源头保证可测试性：

| 原则 | 说明 | 示例 |
|------|------|------|
| **构造器注入** | Service 统一用构造器注入依赖，不用 `@Autowired` 字段注入 | 便于 `@InjectMocks` 自动注入，也便于手动 `new` |
| **面向接口编程** | Service 注入接口而非实现类 | Mock 时 mock 接口即可 |
| **避免静态方法调用** | 工具类静态方法难以 Mock，必要时包装成 Bean | 如 `UUID.randomUUID()` → `IdGenerator` Bean |
| **避免在构造器中做业务** | Bean 初始化时不要调远程接口或 DB | 否则测试加载上下文时就报错 |

```java
// ✅ 构造器注入（项目统一方式）
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final WareFeignClient wareFeignClient;

    // 构造器注入，final 字段保证不可变
    public OrderServiceImpl(OrderMapper orderMapper, WareFeignClient wareFeignClient) {
        this.orderMapper = orderMapper;
        this.wareFeignClient = wareFeignClient;
    }
}

// ❌ 字段注入（不推荐，难以单元测试）
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;  // 无法在 new 时注入
}
```

> **Lombok 用户**：在 Service 实现类上加 `@RequiredArgsConstructor`，自动生成构造器，配合 `final` 字段即可实现构造器注入。

---

## 三、按代码层的测试要求

### 3.1 Controller 层 — `@WebMvcTest` 切片测试

**目标**：验证 HTTP 层行为（路由、参数校验、响应序列化），不加载 Service/Mapper。

**写法要点**：
- 用 `@WebMvcTest(XxxController.class)` 只加载目标 Controller
- Service 用 `@MockitoBean` Mock（Spring Boot 3.4+，替代已废弃的 `@MockBean`）
- Feign Client 用 `@MockitoBean` Mock
- 用 `MockMvc` 发请求，断言 HTTP 状态码 + JSON 路径

**必须覆盖的场景**：

| 场景 | 示例 |
|------|------|
| 正常请求 | 合法参数 → 200 + 正确数据 |
| 参数校验失败 | 缺少必填字段 → 400 |
| 业务异常 | Service 抛异常 → 对应错误码 |
| 空结果 | 查询无数据 → 200 + 空列表 |

**示例**（参考 `mall-member/MemberControllerTest.java`）：

```java
@WebMvcTest(MemberController.class)
@ActiveProfiles("test")
@DisplayName("MemberController 远程调用测试")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponFeignClient couponFeignClient;

    @Test
    @DisplayName("应返回 200 并透传远程服务的数据")
    void shouldReturnRemoteCouponList() throws Exception {
        // Given
        R<Map<String, Object>> mockResponse = R.ok()
                .put("coupons", List.of(Map.of("id", 1, "couponName", "满100减20")))
                .put("total", 1);
        when(couponFeignClient.list()).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/member/member/test-remote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.coupons").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
```

### 3.2 Service 层 — 纯单元测试（核心逻辑 TDD）

**目标**：验证业务规则正确性，不依赖 Spring 上下文，速度极快。

**写法要点**：
- **不加任何 Spring 注解**，纯 JUnit 5 + Mockito
- Mapper 用 `@Mock` Mock，手动 `when().thenReturn()` 桩数据
- 用 `@InjectMocks` 注入到 Service 实例（要求构造器注入）
- 断言统一用 **AssertJ**（`assertThat`）
- 核心逻辑走 TDD（RED → GREEN → REFACTOR），样板 CRUD 不强制写

**核心逻辑必须覆盖的场景**：

| 场景类型 | 示例 |
|---------|------|
| 正常流程 | 正常下单 → 订单创建成功 |
| 边界值 | 金额 0.01、库存 1、分页 size=1 |
| 异常流程 | 库存不足 → 抛异常 |
| 并发场景 | 超卖（用 `@RepeatedTest` 或并发模拟） |
| 状态流转 | 订单 状态A → 状态B，非法流转抛异常 |

**示例**：

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("订单创建逻辑")
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private WareFeignClient wareFeignClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Nested
    @DisplayName("创建订单")
    class CreateOrder {

        @Test
        @DisplayName("库存不足时应抛出业务异常")
        void shouldThrowWhenStockNotEnough() {
            // Given: 库存只剩 5
            when(wareFeignClient.getStock(anyLong())).thenReturn(5);

            // When & Then: 下单买 10 个，应抛异常
            OrderCreateDTO dto = OrderCreateDTO.builder().skuId(1L).count(10).build();
            assertThatThrownBy(() -> orderService.createOrder(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("库存不足");
        }

        @Test
        @DisplayName("库存充足时应创建订单并扣减库存")
        void shouldCreateOrderWhenStockEnough() {
            // Given
            when(wareFeignClient.getStock(anyLong())).thenReturn(100);
            when(orderMapper.insert(any())).thenReturn(1);

            // When
            OrderCreateDTO dto = OrderCreateDTO.builder().skuId(1L).count(2).build();
            Long orderId = orderService.createOrder(dto);

            // Then: 验证订单创建 + 库存扣减
            assertThat(orderId).isNotNull();
            verify(wareFeignClient).deductStock(1L, 2);
            verify(orderMapper).insert(any());
        }
    }
}
```

### 3.3 Mapper 层 — `@MybatisPlusTest` 切片测试

**目标**：验证复杂 SQL 查询正确性，用 H2 内存库替代真实 MySQL。

**写法要点**：
- 用 `@MybatisPlusTest` 只加载 Mapper 层
- 配置 H2 内存数据库（自动替换真实 DataSource）
- 用 `@Sql` 或 `@BeforeEach` 初始化测试数据
- 断言用 AssertJ

**写不写**：

| 场景 | 写不写 |
|------|--------|
| `BaseMapper` 单表 CRUD | ❌ 不写（MyBatis-Plus 保证） |
| 自定义 XML SQL（多表关联、复杂条件） | ✅ 写 |
| 分页查询 | ✅ 写（验证分页逻辑） |

**示例**：

```java
@MybatisPlusTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("商品 Mapper 复杂查询测试")
class ProductMapperTest {

    @Autowired
    private ProductMapper productMapper;

    @BeforeEach
    void initData() {
        Product p1 = new Product();
        p1.setName("iPhone");
        p1.setStatus(1);
        productMapper.insert(p1);
    }

    @Test
    @DisplayName("应按分类+状态查询商品列表")
    void shouldQueryByCategoryAndStatus() {
        // When
        List<Product> result = productMapper.selectByCategoryAndStatus(1L, 1);

        // Then
        assertThat(result)
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(1));
    }
}
```

### 3.4 集成测试 — `@SpringBootTest` + WireMock

**目标**：验证 Controller → Feign → HTTP 全链路，包括序列化/反序列化、路由、负载均衡配置。

**写法要点**：
- 用 `@SpringBootTest` 启动完整上下文
- 用 `WireMockExtension` 编程式注册（获取随机端口）
- 用 `@DynamicPropertySource` 将 Feign URL 指向 WireMock
- 禁用 Nacos/LoadBalancer（测试不需要真实服务发现）

**示例**（参考 `mall-member/MemberControllerIT.java`）：

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
        // Given
        WireMock.stubFor(WireMock.get(urlEqualTo("/coupon/coupon/list"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":200,"data":{"coupons":[{"id":1,"couponName":"满100减20"}],"total":1}}
                                """)));

        // When & Then
        mockMvc.perform(get("/member/member/test-remote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coupons").isArray())
                .andExpect(jsonPath("$.data.total").value(1));

        // Verify: Feign 确实发出了 HTTP 请求
        WireMock.verify(getRequestedFor(urlEqualTo("/coupon/coupon/list")));
    }
}
```

### 3.5 E2E 测试 — `@SpringBootTest` + Testcontainers

**目标**：用真实中间件（MySQL/Redis/RocketMQ）验证全链路，包括数据库事务、缓存一致性、消息消费等切片测试和集成测试无法覆盖的场景。

**写法要点**：
- 用 `@SpringBootTest` 启动完整上下文
- 用 Testcontainers 启动真实 MySQL/Redis/RocketMQ 容器（`@Testcontainers` + `@Container`）
- 用 `@DynamicPropertySource` 将数据源/Redis 地址指向容器
- 禁用 Nacos 服务发现（测试不依赖真实注册中心）
- E2E 测试类命名 `*E2ET`，由 `maven-failsafe-plugin` 在 `verify` 阶段执行

**何时写**：

| 场景 | 写不写 |
|------|--------|
| 下单全链路（扣库存 → 创建订单 → 消息通知） | ✅ 写 |
| 秒杀链路（Redis 预减 → MQ 削峰 → 落库） | ✅ 写 |
| 分布式事务回滚（Seata AT） | ✅ 写 |
| 支付回调 → 订单状态流转 | ✅ 写 |
| 普通 CRUD | ❌ 不写（切片/集成测试已覆盖） |

**示例骨架**：

```java
@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("下单全链路 E2E 测试")
class OrderFlowE2ET {

    @Container
    static GenericContainer<?> mysql = new GenericContainer<>("mysql:8.4")
            .withEnv("MYSQL_ROOT_PASSWORD", "test")
            .withExposedPorts(3306);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306) + "/mall_order");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("正常下单应扣减库存并创建订单")
    void shouldCreateOrderAndDeductStock() {
        // Given: 初始化商品库存
        // When: 调用下单接口
        // Then: 订单创建成功 + 库存正确扣减 + 消息已发送
    }
}
```

> **注意**：E2E 测试较慢（10~30s/个），只写关键业务链路，不要为每个接口写。E2E 与集成测试（3.4）的区别：集成测试用 WireMock Mock 远程服务 + H2 内存库；E2E 用真实中间件容器，验证真实的事务、缓存、消息行为。

### 3.6 公共组件测试 — mall-common

**目标**：mall-common 被所有业务服务依赖，其横切组件一旦出 Bug 影响全局，必须有测试保障。

**必须覆盖的组件**：

| 组件 | 测试类型 | 验证点 |
|------|---------|--------|
| `GlobalExceptionHandler` | `@WebMvcTest` 切片 | 各类异常（BizException / ConstraintViolation / Bind / HttpMessageNotReadable / MaxUploadSize）→ 正确错误码 + HTTP 200 |
| `JacksonConfig` | 纯单元 / 切片 | Long→String 序列化防 JS 精度丢失；LocalDateTime 格式化 |
| `MyMetaObjectHandler` | 纯单元 | insertFill / updateFill 正确填充审计字段（createTime/updateTime/createBy/updateBy） |
| `MybatisPlusConfig` | — | 分页插件配置，有 maxLimit 限制（配置类无复杂逻辑可不测） |
| `R` / `PageVO` / `PageQuery` | 纯单元 | 链式 put、分页参数校验 |
| `UserContext` | 纯单元 | ThreadLocal set/get/clear，防止线程间串号 |
| `OssTemplate` | 集成（Testcontainers MinIO） | Presigned URL 签发、文件上传/删除/查询 |

**写法要点**：
- `GlobalExceptionHandler` 用 `@WebMvcTest` 加载一个测试 Controller，故意抛出各类异常，断言响应 JSON 的 code/msg
- `JacksonConfig` 用 `ObjectMapper` 直接序列化断言，不需要 Spring 上下文
- `MyMetaObjectHandler` 纯单元测试，Mock `MetaObject`，验证字段填充

**示例骨架**（GlobalExceptionHandler）：

```java
@WebMvcTest(TestErrorController.class)
@DisplayName("全局异常处理器")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BizException 应返回 HTTP 200 + 对应错误码")
    void shouldHandleBizException() throws Exception {
        mockMvc.perform(get("/test/biz-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.BIZ_ERROR.getCode()));
    }

    @Test
    @DisplayName("参数校验失败应返回 400 错误码")
    void shouldHandleValidationException() throws Exception {
        mockMvc.perform(get("/test/param-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }
}
```

### 3.7 网关过滤器测试 — `WebTestClient`

**目标**：验证 Gateway 全局过滤器（请求日志、TraceId 透传、鉴权）的逻辑正确性。

**写法要点**：
- 用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 启动 Gateway
- 用 `WebTestClient` 发请求，断言响应头/状态码
- 用 `@MockitoBean` Mock 下游服务发现（不需要真实微服务）
- 验证过滤器链执行顺序、请求头透传、TraceId 生成

**必须覆盖的场景**：

| 场景 | 验证点 |
|------|--------|
| 正常请求 | TraceId 生成并写入响应头 |
| 请求日志 | 请求/响应日志正确记录（方法/路径/状态码/耗时） |
| 无效 Token | 鉴权过滤器拒绝并返回 401 |
| 白名单路径 | 登录/注册接口不鉴权，直接放行 |

**示例骨架**：

```java
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.enabled=false"
})
@AutoConfigureWebTestClient
@DisplayName("网关过滤器链测试")
class GatewayFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("响应头应包含 X-Trace-Id")
    void shouldReturnTraceIdInHeader() {
        webTestClient.get().uri("/api/product/category/list/tree")
                .exchange()
                .expectHeader().exists("X-Trace-Id");
    }
}
```

---

## 四、测试命名与结构规范

### 4.1 类命名

| 测试类型 | 命名规则 | 示例 |
|---------|---------|------|
| 纯单元测试 | `{ClassName}Test` | `OrderServiceTest` |
| Controller 切片测试 | `{ClassName}Test` | `MemberControllerTest` |
| Mapper 切片测试 | `{ClassName}Test` | `ProductMapperTest` |
| 集成测试 | `{ClassName}IT` | `MemberControllerIT` |

> `IT` 后缀的测试由 `maven-failsafe-plugin` 在 `verify` 阶段执行，与单元测试分离（surefire 跑 `*Test`，failsafe 跑 `*IT`）。

### 4.2 方法命名

用 `@DisplayName` 写中文描述，方法名用 `should + 期望行为`：

```java
@Test
@DisplayName("库存不足时应抛出业务异常")
void shouldThrowWhenStockNotEnough() { ... }

@Test
@DisplayName("正常下单应创建订单并扣减库存")
void shouldCreateOrderAndDeductStock() { ... }
```

### 4.3 测试结构 — Given-When-Then

每个测试方法分三段，用注释分隔（BDD 风格）：

```java
@Test
@DisplayName("...")
void shouldXxx() {
    // Given: 准备数据（Mock 桩、构造对象）
    when(mapper.selectById(1L)).thenReturn(entity);

    // When: 执行被测方法
    R result = service.getById(1L);

    // Then: 断言结果（用 AssertJ）
    assertThat(result.getCode()).isEqualTo(200);
    assertThat(result.getData()).isNotNull();
    verify(mapper).selectById(1L);
}
```

> When & Then 可以合并（如异常断言 `assertThatThrownBy`），但 Given 必须独立。

### 4.4 嵌套分组

用 `@Nested` 按接口/方法分组，`@DisplayName` 标注场景：

```java
@DisplayName("订单服务")
class OrderServiceTest {

    @Nested
    @DisplayName("创建订单")
    class CreateOrder {
        @Test @DisplayName("库存充足 → 成功")
        void shouldSucceed() { ... }

        @Test @DisplayName("库存不足 → 抛异常")
        void shouldThrow() { ... }
    }

    @Nested
    @DisplayName("取消订单")
    class CancelOrder {
        @Test @DisplayName("未支付订单 → 直接取消")
        void shouldCancelUnpaid() { ... }

        @Test @DisplayName("已发货订单 → 拒绝取消")
        void shouldRejectShipped() { ... }
    }
}
```

---

## 五、测试依赖配置

### 5.1 父 POM（surefire + failsafe + jacoco）

> **⚠️ 现状缺口**：父 POM 当前只有 `maven-surefire-plugin`，**缺少 `maven-failsafe-plugin` 和 `jacoco-maven-plugin`**。直接后果：`*IT.java` 集成测试在 `mvn verify` 时不会执行（surefire 默认不匹配 `*IT`），且无覆盖率统计。**这三者必须在父 POM 中统一配置，子模块继承即可。**

```xml
<build>
    <plugins>
        <!-- 单元测试 + 切片测试（*Test.java），test 阶段执行 -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <!-- JDK 21+: 允许动态 agent 加载，消除 Mockito/ByteBuddy 告警 -->
                <!-- ${argLine} 由 jacoco prepare-agent 注入 agent，两者不能冲突 -->
                <argLine>@{argLine} -XX:+EnableDynamicAgentLoading -Xshare:off</argLine>
            </configuration>
        </plugin>
        <!-- 集成测试（*IT.java），verify 阶段执行 -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <executions>
                <execution>
                    <goals>
                        <goal>integration-test</goal>
                        <goal>verify</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        <!-- 覆盖率统计：prepare-agent 注入 JVM agent，report 生成报告 -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>prepare-agent</id>
                    <goals><goal>prepare-agent</goal></goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals><goal>report</goal></goals>
                </execution>
                <!-- 覆盖率门禁：verify 阶段检查，不达标则构建失败 -->
                <execution>
                    <id>check</id>
                    <goals><goal>check</goal></goals>
                    <configuration>
                        <rules>
                            <rule>
                                <element>BUNDLE</element>
                                <limits>
                                    <limit>
                                        <counter>LINE</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.60</minimum>
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

> surefire 默认匹配 `*Test`、`*Tests`；failsafe 默认匹配 `*IT`、`*ITCase`。两者不会重复执行。E2E 测试类命名 `*E2ET`，需在 failsafe 中追加 `<include>**/*E2ET.java</include>` 配置。
>
> **覆盖率门禁阈值**：初始阶段全量 LINE 覆盖率门禁设为 60%（避免历史代码拉低导致构建频繁失败），后续随测试补齐逐步提升至 80%。详见第九章。
>
> **`@{argLine}` 说明**：jacoco 的 `prepare-agent` 目标会将 agent 参数写入 `argLine` 属性，surefire/failsafe 用 `@{argLine}` 引用（`@` 表示延迟属性引用），确保 jacoco agent 和 JDK 21 动态 agent 参数同时生效。

### 5.2 各业务模块 POM

每个需要测试的模块在 `pom.xml` 中声明以下 test scope 依赖：

```xml
<!-- Spring Boot Test（含 JUnit 5 + Mockito + AssertJ） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- WireMock（有 Feign 调用的模块才需要） -->
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.9.2</version>
    <scope>test</scope>
</dependency>

<!-- H2（有数据库操作的模块才需要） -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>

<!-- Testcontainers（E2E 测试模块才需要） -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- Spring Boot Starter for WebFlux WebTestClient（网关模块才需要） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
    <scope>test</scope>
</dependency>
```

### 5.3 测试资源文件

有数据库操作的模块，在 `src/test/resources/` 下创建：

```
src/test/resources/
├── application-test.yml          # 测试环境配置（H2 + 禁用 Nacos）
└── schema.sql / data.sql         # H2 初始化脚本（如需）
```

`application-test.yml` 模板：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      mode: never
  cloud:
    nacos:
      discovery:
        enabled: false
      config:
        enabled: false
    loadbalancer:
      enabled: false
    sentinel:
      enabled: false
```

> **⚠️ 现状缺口**：目前仅 `mall-oss`、`mall-product` 有 `application-test.yml`，其余模块 `src/test/resources/` 为空 `.gitkeep`。**各业务模块启动 Mapper 切片测试前，必须先补齐 `application-test.yml`**。E2E 测试模块需额外准备 `schema.sql`（建表脚本，从 `init/` 目录同步）。

### 5.4 测试依赖管理（父 POM `<dependencyManagement>`）

以下测试依赖版本在父 POM 统一管理，子模块声明时无需写 `<version>`：

| 依赖 | 版本 | 说明 |
|------|------|------|
| `spring-boot-starter-test` | Spring Boot 3.4 管理 | JUnit 5 + Mockito + AssertJ + Spring Boot Test |
| `wiremock-standalone` | 3.9.2 | WireMock HTTP Mock |
| `h2` | Spring Boot 3.4 管理 | 内存数据库 |
| `testcontainers` BOM | 1.20+ | Testcontainers 版本统一管理 |
| `spring-boot-starter-webflux` | Spring Boot 3.4 管理 | WebTestClient（网关测试） |

---

## 六、测试目录结构

```
src/test/java/com/mymall/{module}/
├── controller/
│   ├── XxxControllerTest.java        # @WebMvcTest 切片测试
│   └── XxxControllerIT.java          # @SpringBootTest 集成测试（有 Feign 时）
├── service/
│   └── XxxServiceTest.java           # 纯单元测试（核心逻辑 TDD）
├── mapper/
│   └── XxxMapperTest.java            # @MybatisPlusTest（复杂查询才写）
├── e2e/
│   └── XxxFlowE2ET.java              # E2E 测试（Testcontainers，关键链路）
└── ...
```

> mall-common 测试目录结构（横切组件）：
> ```
> src/test/java/com/mymall/common/
> ├── config/
> │   └── JacksonConfigTest.java       # 序列化配置测试
> ├── handler/
> │   └── GlobalExceptionHandlerTest.java  # 全局异常处理器测试
> └── util/
>     └── UserContextTest.java         # ThreadLocal 工具测试
> ```

---

## 七、什么不写测试

| 场景 | 原因 |
|------|------|
| `BaseMapper` 单表 CRUD | MyBatis-Plus 框架保证，不用测 |
| 纯 DTO/VO/Entity | 只有 getter/setter，无逻辑 |
| 代码生成器生成的样板代码 | 按 `docs/mybatis-plus-codegen-guide.md` 生成，不测 |
| Controller 里无分支的透传 | 如果只是 `service.xxx()` → `return R.ok()`，Controller 测试已覆盖 |
| 配置类 | 除非有复杂条件逻辑 |

---

## 八、运行测试

### 8.1 IDEA 中运行

- 单个测试：点击方法名左侧绿色箭头
- 整个测试类：点击类名左侧绿色箭头
- 模块全部测试：右键 `src/test/java` → Run All Tests

### 8.2 Maven 命令

```bash
# 运行单元测试 + 切片测试（surefire，*Test.java）
mvn test -pl mall-member

# 运行集成测试（failsafe，*IT.java）
mvn verify -pl mall-member

# 跳过测试编译打包
mvn package -DskipTests

# 只运行指定测试类
mvn test -pl mall-member -Dtest=MemberControllerTest
```

### 8.3 .http 文件手动验证

开发阶段配合 IDEA HTTP Client 手动调接口，文件放在 `http/` 目录下。测试通过后，手动验证真实环境。

---

## 九、覆盖率门禁（Jacoco）

> **目标**：用覆盖率指标量化测试充分性，CI 中卡点，防止"写了功能不写测试"。

### 9.1 覆盖率指标说明

| 指标 | 说明 | 本项目采用 |
|------|------|-----------|
| LINE（行覆盖率） | 已执行代码行占比 | ✅ 门禁指标 |
| BRANCH（分支覆盖率） | if/else 分支已执行占比 | ✅ 参考指标 |
| METHOD（方法覆盖率） | 已调用方法占比 | 参考指标 |
| CLASS（类覆盖率） | 已覆盖类占比 | 参考指标 |

### 9.2 门禁规则

| 阶段 | LINE 覆盖率 | 适用范围 | 说明 |
|------|-----------|---------|------|
| 初始（当前） | ≥ 60% | 全量 BUNDLE | 历史代码未补测试，阈值宽松避免构建频繁失败 |
| 过渡 | ≥ 70% | 全量 BUNDLE | 补齐 common/gateway 测试后提升 |
| 目标 | ≥ 80% | 全量 BUNDLE | 生产级标准 |

> **增量覆盖率**：后续 CI 成熟后，可引入增量覆盖率检查（只看本次 PR 新增/修改代码的覆盖率），用 Spotbugs/diff-cover 等工具实现，PR 新代码 LINE 覆盖率 ≥ 80%。

### 9.3 排除规则

以下代码不计入覆盖率统计（在 jacoco `excludes` 中配置）：

```xml
<configuration>
    <excludes>
        <!-- 启动类 -->
        <exclude>**/Application.class</exclude>
        <!-- 代码生成器 -->
        <exclude>**/generator/**</exclude>
        <!-- DTO/VO/Entity 等纯模型类 -->
        <exclude>**/entity/**</exclude>
        <exclude>**/dto/**</exclude>
        <exclude>**/vo/**</exclude>
        <!-- MyBatis-Plus 生成的 Mapper -->
        <exclude>**/mapper/impl/**</exclude>
        <!-- 配置类（无复杂逻辑） -->
        <exclude>**/config/*Config.class</exclude>
    </excludes>
</configuration>
```

### 9.4 报告查看

- 本地：`mvn test` 后查看 `target/site/jacoco/index.html`
- CI：GitHub Actions 上传 jacoco 报告 artifact，PR 评论展示覆盖率变化

---

## 十、CI 集成（GitHub Actions）

> **⚠️ 现状缺口**：项目当前只有 `mkdocs.yml` 文档部署 workflow，**没有任何后端 CI workflow 跑测试**。测试只能靠本地手跑，PR 合并不卡测试通过——这是生产级标准下最大的缺口。

### 10.1 CI 流程设计

```
PR / Push to feature branch
  → Checkout 代码
  → Setup JDK 21
  → Cache Maven 依赖
  → mvn verify -B（surefire 单元/切片 + failsafe 集成/E2E + jacoco 报告）
  → 上传 jacoco 报告 artifact
  → PR 评论展示覆盖率（可选，用 jacoco-report-action）
```

### 10.2 workflow 骨架

```yaml
name: Backend CI

on:
  pull_request:
    branches: [ main, develop ]
  push:
    branches: [ develop ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Run tests
        run: mvn verify -B -pl !mall-admin-frontend
      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-reports
          path: '**/target/site/jacoco/'
```

### 10.3 分阶段引入策略

E2E 测试依赖 Docker（Testcontainers），CI 环境需支持 Docker：

| 阶段 | CI 执行范围 | 说明 |
|------|-----------|------|
| 阶段 1 | `mvn test`（surefire 单元/切片） | 不需要 Docker，快速反馈 |
| 阶段 2 | `mvn verify`（+ failsafe 集成测试） | WireMock 不需要 Docker，但仍需运行 |
| 阶段 3 | `mvn verify`（+ E2E Testcontainers） | GitHub Actions runner 自带 Docker |

> **PR 合并门禁**：CI 测试通过是 PR 合并的必要条件（GitHub Branch Protection Rules → Require status checks to pass）。阶段 1 起即启用。

### 10.4 多模块并行优化（后期）

项目模块多，全量 `mvn verify` 较慢。后期可用 `-T 1C`（每核一个线程）并行构建，或按模块拆分多个 job 并行执行。

---

## 十一、前端测试体系（待建立）

> **⚠️ 现状缺口**：mall-admin-frontend 当前零测试（无 vitest 配置、无 spec 文件），测试规范也只覆盖后端。前端有 PageTable/FormDialog 通用组件、useTable/useDialog composables、树形工具函数等可测逻辑。
>
> 本章节为规划性质，待前端测试体系建立时补充详细写法并独立成文 `docs/frontend/testing-specification.md`。

### 11.1 工具链

| 工具 | 作用 | 说明 |
|------|------|------|
| **Vitest** | 测试框架 | Vite 原生，极速 HMR，API 兼容 Jest |
| **Vue Test Utils** | 组件挂载/交互 | `mount` / `shallowMount` / `findComponent` |
| **@vue/test-utils** | Vue 3 官方测试库 | 模拟用户交互、组件 props/slots |
| **happy-dom** | DOM 模拟 | 比 jsdom 更快，Vitest 推荐 |
| **msw** | API Mock | Mock Service Worker，拦截 axios 请求 |

### 11.2 测试分层

| 层级 | 类型 | 工具 | 写不写 |
|------|------|------|--------|
| 工具函数 | 纯单元 | Vitest | 必须（树形工具、格式化、校验等） |
| Composables | 单元 | Vitest + `@vueuse/core` 的 `useTimeoutPoll` 等 | 必须（useTable/useDialog 逻辑） |
| 通用组件 | 组件测试 | Vue Test Utils | 必须（PageTable/FormDialog） |
| 页面组件 | 组件测试 | Vue Test Utils + msw | 关键页面写 |
| E2E | 端到端 | Playwright / Cypress | 后期关键流程写 |

### 11.3 必须覆盖的前端逻辑

| 对象 | 文件位置 | 验证点 |
|------|---------|--------|
| 树形工具函数 | `src/utils/tree.ts` | listToTree / treeToList / findNode / 拖拽排序后层级校验 |
| useTable | `src/composables/useTable.ts` | 分页加载、刷新、搜索、loading 状态 |
| useDialog | `src/composables/useDialog.ts` | 打开/关闭、表单回填、提交后刷新 |
| PageTable | `src/components/PageTable/` | 列渲染、分页交互、空数据、操作列插槽 |
| FormDialog | `src/components/FormDialog/` | 表单校验、提交、关闭重置 |
| request 拦截器 | `src/utils/request.ts` | R\<T\> 剥离、401 跳转、错误提示 |

### 11.4 配置骨架（待实现）

```ts
// vitest.config.ts
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'happy-dom',
    globals: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      thresholds: { lines: 60, branches: 60 }
    }
  }
})
```

### 11.5 前端 CI 集成

前端 CI 与后端分离，独立 job：

```yaml
  frontend-test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: mall-admin-frontend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: mall-admin-frontend/package-lock.json
      - run: npm ci
      - run: npm run test:unit -- --coverage
      - run: npm run type-check
```

---

## 十二、测试清单（提交前自查）

开发完一个功能，提交前对照清单：

- [ ] **核心逻辑**有纯单元测试？覆盖了正常/异常/边界场景？
- [ ] **Controller** 有 `@WebMvcTest` 切片测试？覆盖了正常 + 参数校验失败？
- [ ] **有 Feign 调用**的接口有 `@SpringBootTest` + WireMock 集成测试？
- [ ] **复杂 SQL** 有 Mapper 切片测试？
- [ ] **公共组件**（GlobalExceptionHandler / JacksonConfig / MyMetaObjectHandler）有测试？
- [ ] **网关过滤器**有 `WebTestClient` 切片测试？
- [ ] **关键业务链路**有 E2E 测试（Testcontainers）？
- [ ] 断言统一用 **AssertJ**（`assertThat`），没有用 JUnit 原生断言？
- [ ] 测试方法命名清晰，用了 `@DisplayName` 中文描述？
- [ ] 测试结构遵循 Given-When-Then？
- [ ] Service 用构造器注入，`@InjectMocks` 能正常注入？
- [ ] `mvn verify` 全部通过（surefire + failsafe + jacoco 门禁）？
- [ ] 没有为样板代码写无意义的测试？
- [ ] 新增/修改代码的 LINE 覆盖率达标（当前 ≥ 60%，目标 ≥ 80%）？

---

## 十三、与开发流程的关系

本规范是 `docs/development-workflow.md` 阶段2（实现）的执行细则：

```
development-workflow.md          testing-specification.md
┌──────────────────┐             ┌──────────────────────┐
│ 阶段2: 实现       │             │ Controller → @WebMvcTest │
│ ├ 核心逻辑 → TDD │ ────────►   │ Service   → 纯单元测试   │
│ └ 样板代码 → 生成 │             │ Mapper    → @MybatisPlusTest │
│                  │             │ Feign链路 → @SpringBootTest │
│ 阶段3: 验证       │ ────────►   │ mvn verify 全绿 + 覆盖率达标 │
└──────────────────┘             └──────────────────────┘
```

---

## 十四、待补充事项清单（行动清单）

> 以下为本规范梳理出的测试体系欠缺项，按优先级排列，作为后续补齐工作的行动清单。每项完成后在进度文档 `docs/PROGRESS.md` 记录。

### P0 — 规范与实现不一致（立即修复）

| # | 事项 | 涉及文件 | 说明 |
|---|------|---------|------|
| 1 | 父 POM 补 `maven-failsafe-plugin` | `pom.xml` | 当前缺失，导致 `*IT.java` 集成测试不执行 |
| 2 | 父 POM 补 `jacoco-maven-plugin` | `pom.xml` | 当前无覆盖率统计和门禁 |
| 3 | surefire `argLine` 改为 `@{argLine}` 配合 jacoco | `pom.xml` | 见 5.1 节说明 |

### P1 — CI 闭环（最高优先级）

| # | 事项 | 涉及文件 | 说明 |
|---|------|---------|------|
| 4 | 新增后端 CI workflow | `.github/workflows/backend-ci.yml` | PR/Push 跑 `mvn verify`，见第十章骨架 |
| 5 | 启用 PR 合并门禁 | GitHub Branch Protection Rules | CI 通过才能合并 PR |

### P2 — 公共组件与网关补测

| # | 事项 | 涉及文件 | 说明 |
|---|------|---------|------|
| 6 | `GlobalExceptionHandler` 切片测试 | `mall-common` | 覆盖各类异常 → 错误码映射，见 3.6 节 |
| 7 | `JacksonConfig` 序列化测试 | `mall-common` | Long→String、LocalDateTime 格式化 |
| 8 | `MyMetaObjectHandler` 单元测试 | `mall-common` | 审计字段自动填充 |
| 9 | `UserContext` 单元测试 | `mall-common` | ThreadLocal set/get/clear |
| 10 | 网关 `RequestLogFilter` / `TraceIdFilter` 测试 | `mall-gateway` | WebTestClient 切片测试，见 3.7 节 |
| 11 | 其余模块补齐 `application-test.yml` | 各模块 `src/test/resources/` | 见 5.3 节缺口说明 |

### P3 — E2E 测试体系

| # | 事项 | 涉及文件 | 说明 |
|---|------|---------|------|
| 12 | 父 POM `<dependencyManagement>` 引入 Testcontainers BOM | `pom.xml` | 统一版本管理 |
| 13 | failsafe 追加 `*E2ET` include 配置 | `pom.xml` | 确保 E2E 测试在 verify 阶段执行 |
| 14 | 下单全链路 E2E 测试 | `mall-order` | 扣库存 → 创建订单 → 消息通知，见 3.5 节 |
| 15 | 秒杀链路 E2E 测试 | `mall-seckill` | Redis 预减 → MQ 削峰 → 落库 |

### P4 — 前端测试体系

| # | 事项 | 涉及文件 | 说明 |
|---|------|---------|------|
| 16 | 安装 vitest + vue-test-utils + happy-dom 依赖 | `mall-admin-frontend/package.json` | 见第十一章工具链 |
| 17 | 创建 `vitest.config.ts` | `mall-admin-frontend/` | 覆盖率配置，见 11.4 节 |
| 18 | 树形工具函数单元测试 | `src/utils/__tests__/tree.spec.ts` | 见 11.3 节 |
| 19 | useTable / useDialog composables 测试 | `src/composables/__tests__/` | 见 11.3 节 |
| 20 | PageTable / FormDialog 组件测试 | `src/components/__tests__/` | 见 11.3 节 |
| 21 | request 拦截器测试 | `src/utils/__tests__/request.spec.ts` | R\<T\> 剥离、401 跳转 |
| 22 | 前端 CI job | `.github/workflows/backend-ci.yml` 或独立 workflow | 见 11.5 节骨架 |
| 23 | 前端测试规范独立成文 | `docs/frontend/testing-specification.md` | 详细写法、命名、结构规范 |

### P5 — 覆盖率提升与门禁收紧

| # | 事项 | 涉及文件 | 说明 |
|---|------|---------|------|
| 24 | jacoco 排除规则配置 | `pom.xml` | 排除 entity/dto/vo/config 等，见 9.3 节 |
| 25 | 覆盖率门禁从 60% → 70% → 80% | `pom.xml` | 分阶段提升，见 9.2 节 |
| 26 | 增量覆盖率检查（PR 级别） | CI workflow | 后期引入，新代码 ≥ 80% |
