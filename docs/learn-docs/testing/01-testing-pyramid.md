# 01 · 测试分层思想

> 看完这篇你会理解：为什么测试不能"只写一种"、测试金字塔是什么、本项目四层测试各解决什么问题、什么代码不需要写测试。

---

## 一、为什么测试要分层

先看一个反例——如果所有测试都写成"启动整个应用 + 真实数据库"：

```java
// ❌ 只写这一种测试的后果
@SpringBootTest
class OrderTest {
    @Test
    void testCreateOrder() {
        // 启动 Spring 上下文（3 秒）
        // 连接真实 MySQL（要保证数据库开着）
        // 连接真实 Redis（要保证 Redis 开着）
        // 调用下单接口
        // 断言结果
    }
}
```

这种测试能跑通，但有三个致命问题：

| 问题 | 说明 |
|------|------|
| **慢** | 每个测试要启动 Spring + 连数据库，5~30 秒一个。100 个测试跑半小时，没人愿意跑 |
| **不稳定** | 依赖真实环境，数据库挂了/网络抖了都会导致测试"偶发失败"（Flaky Test） |
| **定位难** | 测试失败时，你不知道是业务逻辑错了、还是数据库连不上、还是 Spring 配置有问题 |

反过来，如果只写"纯内存单元测试"，又有另一个问题：

```java
// 纯单元测试：Mock 所有依赖
@Test
void testCreateOrder() {
    // Mock 了 Mapper、Mock 了 Feign
    // 验证了业务逻辑
    // 但：Controller 的路由对不对？参数校验有没有生效？JSON 序列化对不对？——都不知道
}
```

**结论**：不同类型的 bug 出现在不同层，需要不同粒度的测试去抓。

| Bug 类型 | 例子 | 谁来抓 |
|---------|------|--------|
| 业务逻辑错 | 库存不足没抛异常 | 单元测试 |
| HTTP 层错 | 参数校验没生效、路由写错 | 切片测试 |
| 跨服务集成错 | Feign 序列化失败、远程返回结构变了 | 集成测试 |
| 全链路错 | 事务没回滚、缓存不一致、消息没消费 | E2E 测试 |

这就是为什么测试要分层——**每层只负责抓自己那层的 bug，用最小的代价（速度+稳定性）换取最大的覆盖**。

---

## 二、测试金字塔

业界有个经典模型叫"测试金字塔"：

```
        /\
       /  \        E2E 测试（少）
      /    \       最慢最贵，只测关键链路
     /------\
    /        \     集成测试（中）
   /          \    测跨层/跨服务协作
  /------------\
 /              \  切片测试（较多）
/________________\ 测某一层
                  单元测试（最多）最快最便宜，测业务规则
```

**为什么是底大顶小的金字塔？**

越往上越慢越贵，所以数量应该越少：

| 层级 | 速度 | 一个模块大概写多少个 | 跑完耗时 |
|------|------|-------------------|---------|
| 单元测试 | 毫秒级 | 几十~上百个 | 几秒 |
| 切片测试 | 秒级 | 十几个 | 十几秒 |
| 集成测试 | 秒级 | 几个 | 几十秒 |
| E2E 测试 | 十几~几十秒 | 几个关键链路 | 几分钟 |

如果倒过来（冰淇淋模型——顶层多底层少），CI 会变得又慢又不稳定，最终团队会放弃跑测试。

> **比例参考**：单元测试 70%、切片+集成 20%、E2E 10%。不是硬性规定，是投入产出比的平衡。

---

## 三、本项目四层测试

my-mall 是 Java 21 + Spring Boot 3.4 项目，对应四层测试：

### 3.1 单元测试（Unit）

**是什么**：测单个类的方法，不启动 Spring，Mock 所有外部依赖。

**工具**：JUnit 5 + Mockito + AssertJ

**测什么**：业务规则。比如"库存不足时下单应抛异常"、"订单状态从 A 流转到 B 是否合法"。

```java
// 纯 JUnit 5，不加任何 Spring 注解
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderMapper orderMapper;       // Mock 掉数据库
    @Mock WareFeignClient wareFeignClient; // Mock 掉远程调用
    @InjectMocks OrderServiceImpl orderService;

    @Test
    void shouldThrowWhenStockNotEnough() {
        when(wareFeignClient.getStock(1L)).thenReturn(5); // 库存只剩 5
        // 下单买 10 个，应抛异常
        assertThatThrownBy(() -> orderService.createOrder(dto))
            .isInstanceOf(BizException.class);
    }
}
```

**为什么最快**：纯内存运算，不碰数据库、不碰网络、不启动 Spring。1000 个单元测试跑完也就几秒。

### 3.2 切片测试（Slice）

**是什么**：只加载某一层（比如只加载 Controller 层），其余 Mock。

**工具**：`@WebMvcTest` + MockMvc

**测什么**：HTTP 层行为。路由对不对、参数校验有没有生效、响应 JSON 格式对不对、异常处理器返回什么错误码。

```java
@WebMvcTest(OrderController.class)  // 只加载 OrderController
class OrderControllerTest {
    @MockitoBean OrderService orderService; // Service 用 Mock
    @Autowired MockMvc mockMvc;

    @Test
    void shouldReturn400WhenParamMissing() throws Exception {
        mockMvc.perform(post("/order/create")) // 缺少必填参数
            .andExpect(status().isOk())        // 本项目统一 HTTP 200
            .andExpect(jsonPath("$.code").value(400)); // 错误码在 body 里
    }
}
```

**和单元测试的区别**：切片测试会启动一个**轻量 Spring 上下文**（只装 Controller + MVC 相关组件），所以能验证路由、参数校验、序列化这些 Spring MVC 的行为。单元测试完全脱离 Spring，验证不了这些。

### 3.3 集成测试（Integration）

**是什么**：启动完整 Spring 上下文，用 WireMock 模拟远程服务，用 H2 内存库替代 MySQL。

**工具**：`@SpringBootTest` + WireMock

**测什么**：跨服务链路。Controller → Feign → HTTP → 反序列化 全链路是否通畅。

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class MemberControllerIT {
    @RegisterExtension
    static WireMockExtension wireMock = ...; // 启动一个假的 HTTP 服务

    @DynamicPropertySource
    static void configureFeign(DynamicPropertyRegistry r) {
        // 把 Feign 的目标地址指向 WireMock，绕过 Nacos
        r.add("spring.cloud.openfeign.client.config.mall-coupon.url",
              () -> "http://localhost:" + wireMock.getPort());
    }

    @Test
    void shouldCallRemoteServiceAndReturnData() {
        // WireMock 假装是 coupon 服务，返回预设数据
        WireMock.stubFor(get("/coupon/list").willReturn(okJson("...")));
        // 调 member 接口，它内部通过 Feign 调 coupon（实际打到 WireMock）
        mockMvc.perform(get("/member/test-remote"))
            .andExpect(jsonPath("$.data.coupons").isArray());
        // 验证 Feign 确实发出了 HTTP 请求
        WireMock.verify(getRequestedFor(urlEqualTo("/coupon/list")));
    }
}
```

**和切片测试的区别**：切片测试 Mock 掉了 Service，Feign 根本没参与；集成测试让 Feign 真实发出 HTTP 请求（只是目标被换成 WireMock），能验证序列化、路由配置、负载均衡等全链路行为。

### 3.4 E2E 测试（End-to-End）

**是什么**：用 Testcontainers 启动真实的 MySQL/Redis/RocketMQ 容器，验证真实的事务、缓存、消息行为。

**工具**：`@SpringBootTest` + Testcontainers

**测什么**：关键业务全链路。比如"下单：扣库存 → 创建订单 → 发消息通知"，涉及数据库事务、Redis 缓存、RocketMQ 消息，前面三层测试都 Mock 掉了这些，只有 E2E 能验证它们真实的协作。

```java
@Testcontainers
@SpringBootTest
class OrderFlowE2ET {
    @Container static GenericContainer<?> mysql = new GenericContainer<>("mysql:8.4")...;
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7.4")...;

    @Test
    void shouldCreateOrderAndDeductStock() {
        // 真实 MySQL、真实 Redis、真实事务
        // 验证：订单创建成功 + 库存正确扣减 + 消息已发送
    }
}
```

**和集成测试的区别**：集成测试用 WireMock + H2（都是假的）；E2E 用真实中间件容器。E2E 最慢（10~30 秒/个），但最接近生产，只写关键链路。

> 详细的 E2E 方案对比见 [e2e-testing-strategies.md](./e2e-testing-strategies.md)。

---

## 四、四层测试对比总表

| 维度 | 单元测试 | 切片测试 | 集成测试 | E2E 测试 |
|------|---------|---------|---------|---------|
| 注解 | 无（纯 JUnit） | `@WebMvcTest` | `@SpringBootTest` | `@SpringBootTest` + `@Testcontainers` |
| Spring 上下文 | 不启动 | 轻量（只装一层） | 完整 | 完整 + 真实容器 |
| 数据库 | Mock 掉 | Mock 掉 | H2 内存库 | 真实 MySQL 容器 |
| 远程服务 | Mock 掉 | Mock 掉 | WireMock 模拟 | 真实服务 / 容器 |
| 速度 | 毫秒级 | ~2 秒 | ~5 秒 | 10~30 秒 |
| 验证重点 | 业务规则 | HTTP 层行为 | 跨服务链路 | 全链路真实行为 |
| 写不写 | 核心逻辑必须 | 必须 | 有 Feign 时写 | 关键链路写 |
| 类名后缀 | `*Test` | `*Test` | `*IT` | `*E2ET` |
| 谁执行 | surefire | surefire | failsafe | failsafe |

> surefire/failsafe 的区别见 [../ci/03-ci-testing-strategy.md](../ci/03-ci-testing-strategy.md) 第三节。简单记：`*Test` 跑得快（mvn test），`*IT`/`*E2ET` 跑得慢（mvn verify）。

---

## 五、什么代码不写测试

测试不是越多越好。以下代码写测试是浪费时间：

| 场景 | 为什么不测 |
|------|----------|
| `BaseMapper` 单表 CRUD | MyBatis-Plus 框架保证，你测的是框架不是自己的代码 |
| 纯 DTO/VO/Entity | 只有 getter/setter，没有逻辑 |
| 代码生成器生成的样板代码 | 框架生成的，按规范生成即可 |
| Controller 里无分支的透传 | 如果只是 `service.xxx()` → `return R.ok()`，Controller 切片测试已覆盖 |
| 配置类 | 除非有复杂条件逻辑 |

**原则**：测"有逻辑的代码"（Service 业务规则、Controller 路由校验、复杂 SQL、工具类），不测"没逻辑的代码"（模型类、样板 CRUD）。

---

## 六、小结

| 知识点 | 一句话 |
|--------|--------|
| 为什么分层 | 不同层的 bug 需要不同粒度的测试去抓，用最小代价换最大覆盖 |
| 测试金字塔 | 底层多（快又便宜）、顶层少（慢又贵） |
| 单元测试 | 纯内存，测业务规则，最快 |
| 切片测试 | 轻量 Spring，测 HTTP 层 |
| 集成测试 | 完整 Spring + WireMock，测跨服务链路 |
| E2E 测试 | 真实中间件容器，测全链路，最慢 |
| 不写测试 | 模型类、样板 CRUD、无逻辑配置类 |

下一篇 [02-unit-testing.md](./02-unit-testing.md) 会详细讲单元测试怎么写——JUnit 5、Mockito、AssertJ 三个核心工具的实际用法。
