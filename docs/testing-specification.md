# 后端测试规范

> 明确每种接口/逻辑该写什么测试、怎么写，保持全项目风格统一。
> 与 `docs/development-workflow.md`（TDD 流程）配合使用。
>
> **核心原则**：采用业界成熟方案，所学内容可直接迁移到生产环境。

---

## 一、测试分层总览

```
              ┌──────────────────────────────────┐
              │           E2E 测试               │  Testcontainers（后期关键链路）
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
| 全链路 | E2E | `@SpringBootTest` + Testcontainers | 慢（10~30s） | 后期关键链路写 |

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
| **Testcontainers** | 真实中间件测试 | E2E 阶段使用（后期引入） |

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

### 5.1 父 POM（surefire + failsafe）

```xml
<build>
    <plugins>
        <!-- 单元测试 + 切片测试（*Test.java） -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <!-- JDK 21+: 允许动态 agent 加载，消除 Mockito/ByteBuddy 告警 -->
                <argLine>-XX:+EnableDynamicAgentLoading -Xshare:off</argLine>
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
    </plugins>
</build>
```

> surefire 默认匹配 `*Test`、`*Tests`；failsafe 默认匹配 `*IT`、`*ITCase`。两者不会重复执行。

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
└── ...
```

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

## 九、测试清单（提交前自查）

开发完一个功能，提交前对照清单：

- [ ] **核心逻辑**有纯单元测试？覆盖了正常/异常/边界场景？
- [ ] **Controller** 有 `@WebMvcTest` 切片测试？覆盖了正常 + 参数校验失败？
- [ ] **有 Feign 调用**的接口有 `@SpringBootTest` + WireMock 集成测试？
- [ ] **复杂 SQL** 有 Mapper 切片测试？
- [ ] 断言统一用 **AssertJ**（`assertThat`），没有用 JUnit 原生断言？
- [ ] 测试方法命名清晰，用了 `@DisplayName` 中文描述？
- [ ] 测试结构遵循 Given-When-Then？
- [ ] Service 用构造器注入，`@InjectMocks` 能正常注入？
- [ ] `mvn test` 全部通过？
- [ ] 没有为样板代码写无意义的测试？

---

## 十、与开发流程的关系

本规范是 `docs/development-workflow.md` 阶段2（实现）的执行细则：

```
development-workflow.md          testing-specification.md
┌──────────────────┐             ┌──────────────────────┐
│ 阶段2: 实现       │             │ Controller → @WebMvcTest │
│ ├ 核心逻辑 → TDD │ ────────►   │ Service   → 纯单元测试   │
│ └ 样板代码 → 生成 │             │ Mapper    → @MybatisPlusTest │
│                  │             │ Feign链路 → @SpringBootTest │
│ 阶段3: 验证       │ ────────►   │ mvn test 全绿            │
└──────────────────┘             └──────────────────────┘
```
