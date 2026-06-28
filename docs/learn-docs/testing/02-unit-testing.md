# 02 · 单元测试核心

> 单元测试是测试金字塔的底座——数量最多、速度最快、写得最多。本篇讲清 JUnit 5 + Mockito + AssertJ 三件套怎么用，看完就能写出符合规范的单元测试。
>
> 前置阅读：[01-testing-pyramid.md](./01-testing-pyramid.md)

---

## 一、什么是单元测试

单元测试 = **测单个类/方法，把所有外部依赖替换成假对象，只验证业务逻辑**。

```
被测对象：OrderServiceImpl（订单服务）
外部依赖：OrderMapper（数据库）、WareFeignClient（远程库存服务）
                ↓ 全部 Mock 成假对象
测试只关心：OrderServiceImpl 自己的业务逻辑对不对
```

为什么不直接连真实数据库测？因为：

| 问题 | 连真实数据库 | Mock 依赖 |
|------|------------|----------|
| 速度 | 每次要连 MySQL，几百毫秒 | 纯内存，毫秒级 |
| 稳定性 | 数据库挂了/数据脏了，测试就挂 | 不受环境影响 |
| 隔离性 | 不知道是业务逻辑错还是数据库错 | 只验证业务逻辑 |

**核心思想**：单元测试只测「你写的代码对不对」，不测「数据库/网络/框架对不对」——那些由集成测试和 E2E 测试负责。

---

## 二、JUnit 5 基础

JUnit 5 是 Java 测试框架的标准，Spring Boot 3 默认集成。你只需要记住几个核心注解。

### 2.1 最基本的测试

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class CalculatorTest {

    @Test
    @DisplayName("两数相加应返回正确结果")
    void shouldAddTwoNumbers() {
        Calculator calc = new Calculator();
        int result = calc.add(2, 3);
        assertThat(result).isEqualTo(5);  // AssertJ 断言，后面讲
    }
}
```

| 注解 | 作用 |
|------|------|
| `@Test` | 标记这是一个测试方法 |
| `@DisplayName` | 测试的中文描述，出现在测试报告里 |

> **注意**：JUnit 5 的包是 `org.junit.jupiter.api`，不是 JUnit 4 的 `org.junit`。两者不兼容，别导错包。

### 2.2 嵌套分组：@Nested

当一个类有多个方法要测时，用 `@Nested` 按方法分组，测试报告更清晰：

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
        @Test @DisplayName("未支付 → 直接取消")
        void shouldCancelUnpaid() { ... }
    }
}
```

测试报告中会显示成树状结构：

```
订单服务
├── 创建订单
│   ├── 库存充足 → 成功 ✓
│   └── 库存不足 → 抛异常 ✓
└── 取消订单
    └── 未支付 → 直接取消 ✓
```

### 2.3 参数化测试：@ParameterizedTest

同一个逻辑要测多组输入时，别复制粘贴写 5 个测试方法，用参数化测试：

```java
@ParameterizedTest
@DisplayName("各金额应正确计算折扣")
@CsvSource({
    "100, 80",    // 原价100，打8折，应付80
    "200, 160",
    "0, 0",
    "50, 40"
})
void shouldCalculateDiscount(double original, double expected) {
    double result = discountService.calculate(original);
    assertThat(result).isEqualTo(expected);
}
```

一个方法测 4 组数据，比写 4 个方法干净得多。

### 2.4 生命周期方法

```java
class OrderServiceTest {

    @BeforeEach
    void setUp() {
        // 每个测试方法执行前都会跑一次
        // 常用：初始化 Mock、构造测试数据
    }

    @AfterEach
    void tearDown() {
        // 每个测试方法执行后跑一次
        // 常用：清理 ThreadLocal、重置状态
    }
}
```

> `@BeforeEach` vs `@BeforeAll`：前者每个测试方法前都跑（实例方法），后者整个类只跑一次（静态方法）。90% 的场景用 `@BeforeEach`。

---

## 三、Mockito：替换外部依赖

### 3.1 为什么要 Mock

假设要测 `OrderServiceImpl.createOrder()`，它的依赖是：

```java
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;           // 数据库
    private final WareFeignClient wareFeignClient;   // 远程库存服务

    public Long createOrder(OrderCreateDTO dto) {
        int stock = wareFeignClient.getStock(dto.getSkuId());  // 调远程
        if (stock < dto.getCount()) {
            throw new BizException("库存不足");
        }
        Order order = new Order();
        order.setSkuId(dto.getSkuId());
        orderMapper.insert(order);                            // 写数据库
        wareFeignClient.deductStock(dto.getSkuId(), dto.getCount());
        return order.getId();
    }
}
```

如果连真实数据库和远程服务测，又慢又不稳定。Mockito 的作用：**造一个假的 `OrderMapper` 和 `WareFeignClient`，你控制它们返回什么，这样就能精确测试各种场景**（库存充足/库存不足/远程服务超时等）。

### 3.2 Mock 的三步走

```java
@ExtendWith(MockitoExtension.class)   // 启用 Mockito
class OrderServiceTest {

    @Mock                              // 1. 造一个假对象
    private OrderMapper orderMapper;

    @Mock
    private WareFeignClient wareFeignClient;

    @InjectMocks                       // 2. 把假对象注入到被测对象
    private OrderServiceImpl orderService;

    @Test
    @DisplayName("库存不足时应抛异常")
    void shouldThrowWhenStockNotEnough() {
        // 3. 控制假对象的行为（打桩）
        when(wareFeignClient.getStock(1L)).thenReturn(5);  // 假装库存只有5

        OrderCreateDTO dto = OrderCreateDTO.builder().skuId(1L).count(10).build();
        assertThatThrownBy(() -> orderService.createOrder(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存不足");
    }
}
```

三个关键注解：

| 注解 | 作用 |
|------|------|
| `@Mock` | 造一个假对象（所有方法默认返回 null/0/false） |
| `@InjectMocks` | 创建被测对象，把 `@Mock` 的对象自动注入进去 |
| `@ExtendWith(MockitoExtension.class)` | 启用 Mockito 支持，让上面两个注解生效 |

### 3.3 打桩：when().thenReturn()

「打桩」就是控制 Mock 对象的方法返回什么值：

```java
// 调用 getStock(1L) 时返回 5
when(wareFeignClient.getStock(1L)).thenReturn(5);

// 调用 getStock(任何参数) 时返回 100
when(wareFeignClient.getStock(anyLong())).thenReturn(100);

// 调用时抛异常（测试异常处理逻辑）
when(wareFeignClient.getStock(1L)).thenThrow(new RuntimeException("超时"));

// void 方法抛异常用 doThrow
doThrow(new RuntimeException("扣减失败"))
        .when(wareFeignClient).deductStock(anyLong(), anyInt());
```

| 场景 | 写法 |
|------|------|
| 返回指定值 | `when(mock.method(args)).thenReturn(value)` |
| 返回任意参数匹配 | `when(mock.method(anyLong())).thenReturn(value)` |
| 抛异常（有返回值） | `when(mock.method(args)).thenThrow(exception)` |
| 抛异常（void 方法） | `doThrow(exception).when(mock).voidMethod(args)` |

> **`anyLong()` 等参数匹配器**：`anyLong()` 匹配任意 long 值，`anyString()` 匹配任意字符串，`eq(1L)` 精确匹配。**注意：要么全用匹配器，要么全用精确值，不能混用**（`when(mock.method(anyLong(), 5))` 是错的，要写 `when(mock.method(anyLong(), eq(5)))`）。

### 3.4 验证：verify()

除了断言返回值，有时还要验证「某个方法确实被调用了」，用 `verify()`：

```java
@Test
@DisplayName("库存充足时应创建订单并扣减库存")
void shouldCreateOrderAndDeductStock() {
    when(wareFeignClient.getStock(anyLong())).thenReturn(100);
    when(orderMapper.insert(any())).thenReturn(1);

    OrderCreateDTO dto = OrderCreateDTO.builder().skuId(1L).count(2).build();
    orderService.createOrder(dto);

    // 验证扣减库存被调用了，参数是 1L 和 2
    verify(wareFeignClient).deductStock(1L, 2);
    // 验证订单确实写入了数据库
    verify(orderMapper).insert(any());
}
```

```java
// 验证方法「从未被调用」
verify(wareFeignClient, never()).deductStock(anyLong(), anyInt());

// 验证方法「恰好被调用 N 次」
verify(orderMapper, times(2)).insert(any());
```

> **什么时候用 verify**：当被测方法没有返回值（void），或返回值不能完全反映行为时（比如「扣减库存」这个动作是否发生），用 verify 验证副作用。如果方法有返回值，优先断言返回值，而不是 verify。

### 3.5 lenient()：宽松打桩

默认情况下，Mockito 会检查你打的所有桩是否都被用到。如果打了桩但测试没调到，会报 `UnnecessaryStubbingException`。

有时多个测试共享 `@BeforeEach` 打桩，但不是每个测试都会用到所有桩。这时用 `lenient()` 放宽限制：

```java
@BeforeEach
void setUp() {
    // 这个桩可能不是每个测试都用到的，用 lenient 避免警告
    lenient().when(ossTemplate.buildPublicUrl(anyString(), anyString()))
            .thenReturn("http://mock/url");
}
```

> 参考项目 [OssServiceTest.java](../../../mall-oss/src/test/java/com/mymall/oss/service/OssServiceTest.java) 的 setUp 方法，大量用了 `lenient()`。

---

## 四、AssertJ：流式断言库

### 4.1 为什么不用 JUnit 原生断言

```java
// ❌ JUnit 原生断言：可读性差，失败信息不友好
assertEquals(200, result.getCode());
assertNotNull(result.getData());
assertTrue(list.contains(expected));

// 失败时只告诉你：
// expected: 200 but was: 404
```

```java
// ✅ AssertJ：流式 API，失败信息丰富
assertThat(result.getCode()).isEqualTo(200);
assertThat(result.getData()).isNotNull();
assertThat(list).contains(expected).hasSize(3);

// 链式断言，一行搞定复合校验
assertThat(coupons)
    .hasSize(2)
    .extracting("couponName")
    .containsExactly("满100减20", "新人8折券");

// 失败时告诉你：
// Expecting size of: <[coupon1, coupon2, coupon3]> to be 2 but was 3
```

**本项目统一用 AssertJ 替代 JUnit 原生断言**。Spring Boot Test 自带，不用额外加依赖。

### 4.2 常用断言速查

```java
// 基本类型
assertThat(result).isEqualTo(200);
assertThat(count).isGreaterThan(0);
assertThat(price).isBetween(0.0, 100.0);

// 字符串
assertThat(name).isEqualTo("iPhone");
assertThat(name).startsWith("i").endsWith("e");
assertThat(name).isNotBlank();
assertThat(name).contains("Phone");

// 集合
assertThat(list).isNotEmpty();
assertThat(list).hasSize(3);
assertThat(list).contains(element1, element2);
assertThat(list).allSatisfy(item -> assertThat(item.getStatus()).isEqualTo(1));

// 对象字段
assertThat(result).hasFieldOrPropertyWithValue("code", 200);
assertThat(result).isNotNull();

// 异常断言（最常用）
assertThatThrownBy(() -> orderService.createOrder(dto))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("库存不足")
        .hasFieldOrPropertyWithValue("code", ResultCode.STOCK_NOT_ENOUGH.getCode());

// 不抛异常的断言
assertThatCode(() -> service.method()).doesNotThrowAnyException();
```

### 4.3 异常断言详解

异常断言是单元测试里高频操作，重点讲：

```java
@Test
@DisplayName("库存不足时应抛出业务异常")
void shouldThrowWhenStockNotEnough() {
    // Given
    when(wareFeignClient.getStock(anyLong())).thenReturn(5);

    // When & Then 合并：直接断言「调用会抛异常」
    assertThatThrownBy(() -> {
        OrderCreateDTO dto = OrderCreateDTO.builder().skuId(1L).count(10).build();
        orderService.createOrder(dto);
    })
    .isInstanceOf(BizException.class)           // 异常类型
    .hasMessageContaining("库存不足")            // 异常消息包含
    .hasFieldOrPropertyWithValue("code", 50001); // 异常对象的 code 字段
}
```

> `assertThatThrownBy` 接收一个 Lambda（可能会抛异常的代码），返回一个异常断言对象，可以链式断言异常的各种属性。

---

## 五、测试结构：Given-When-Then

本项目所有测试遵循 **Given-When-Then** 三段式结构，用注释分隔：

```java
@Test
@DisplayName("正常下单应创建订单并扣减库存")
void shouldCreateOrderAndDeductStock() {
    // Given: 准备数据（Mock 桩、构造对象）
    when(wareFeignClient.getStock(anyLong())).thenReturn(100);
    when(orderMapper.insert(any())).thenReturn(1);
    OrderCreateDTO dto = OrderCreateDTO.builder().skuId(1L).count(2).build();

    // When: 执行被测方法
    Long orderId = orderService.createOrder(dto);

    // Then: 断言结果（用 AssertJ）
    assertThat(orderId).isNotNull();
    verify(wareFeignClient).deductStock(1L, 2);
    verify(orderMapper).insert(any());
}
```

| 段 | 作用 | 常见操作 |
|----|------|---------|
| Given | 准备测试前提 | `when().thenReturn()` 打桩、构造 DTO |
| When | 执行被测方法 | 调用 service 方法 |
| Then | 验证结果 | `assertThat()` 断言、`verify()` 验证调用 |

> **When 和 Then 可以合并**（如异常断言 `assertThatThrownBy`），但 **Given 必须独立**——这是硬性规范。

---

## 六、可测试性设计：构造器注入

### 6.1 为什么不能用 @Autowired 字段注入

```java
// ❌ 字段注入——没法单元测试
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;  // 无法在 new 时注入

    @Autowired
    private WareFeignClient wareFeignClient;
}
```

用字段注入时，`new OrderServiceImpl()` 后 `orderMapper` 是 null，没法 Mock。`@InjectMocks` 也注入不进去（它靠构造器注入）。

### 6.2 构造器注入——可测试性的基础

```java
// ✅ 构造器注入——可测试
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
```

这样测试时可以手动 new，也可以让 `@InjectMocks` 自动注入：

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock private OrderMapper orderMapper;
    @Mock private WareFeignClient wareFeignClient;

    @InjectMocks  // 自动调用构造器，把上面两个 Mock 传进去
    private OrderServiceImpl orderService;
}
```

> **Lombok 用户**：在 Service 实现类上加 `@RequiredArgsConstructor`，配合 `final` 字段，Lombok 自动生成构造器，等价于手写构造器注入。本项目 Service 统一用这种方式。

### 6.3 其他可测试性原则

| 原则 | 说明 | 反例 |
|------|------|------|
| 构造器注入 | 不用 `@Autowired` 字段注入 | `@Autowired private XxxMapper mapper;` |
| 面向接口 | 注入接口而非实现类 | `private OrderServiceImpl impl;` |
| 避免静态方法 | 静态方法难以 Mock | `UUID.randomUUID()` 直接调用 → 包装成 `IdGenerator` Bean |
| 构造器不做业务 | Bean 初始化时别调远程接口/DB | 构造器里 `feignClient.checkHealth()` |

---

## 七、完整实战：参照项目真实测试

项目的 [OssServiceTest.java](../../../mall-oss/src/test/java/com/mymall/oss/service/OssServiceTest.java) 是单元测试的范本，覆盖了正常/异常/边界/幂等场景。这里拆解它的核心结构：

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("对象存储服务")
class OssServiceTest {

    @Mock
    private OssFileMetaMapper ossFileMetaMapper;

    @Mock
    private OssTemplate ossTemplate;

    private OssServiceImpl ossService;

    @BeforeEach
    void setUp() {
        // 手动 new + 反射注入（因为 OssServiceImpl 继承 ServiceImpl，
        // baseMapper 是父类字段，@InjectMocks 注入不了，需要反射）
        ossService = new OssServiceImpl(ossTemplate);
        ReflectionTestUtils.setField(ossService, "baseMapper", ossFileMetaMapper);
        // 设置 @Value 注入的配置字段
        ReflectionTestUtils.setField(ossService, "allowedBuckets", List.of("mall-product"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();  // 清理 ThreadLocal，防止测试间串号
    }

    @Nested
    @DisplayName("签发上传凭证")
    class CreateUploadPolicy {

        @Test
        @DisplayName("正常签发应返回 PresignedUrlVO 并写入 PENDING 记录")
        void shouldReturnPresignedUrlWhenValid() {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            when(ossTemplate.getPresignedPutUrl(eq("mall-product"), anyString(), eq(300)))
                    .thenReturn("http://mock/url?X-Amz-Signature=xxx");
            lenient().when(ossFileMetaMapper.insert(any(OssFileMeta.class))).thenReturn(1);

            // When
            PresignedUrlVO result = ossService.createUploadPolicy(dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUploadUrl()).contains("X-Amz-Signature");
            assertThat(result.getBucket()).isEqualTo("mall-product");
            verify(ossTemplate).getPresignedPutUrl(eq("mall-product"), anyString(), eq(300));
        }

        @Test
        @DisplayName("Bucket 不在白名单时应抛异常")
        void shouldThrowWhenBucketNotAllowed() {
            // Given
            UploadPolicyDTO dto = buildValidPolicy();
            dto.setBucket("evil-bucket");

            // When & Then
            assertThatThrownBy(() -> ossService.createUploadPolicy(dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.OSS_BUCKET_NOT_ALLOWED.getCode());
        }
    }

    // 辅助方法：构造测试数据，避免每个测试都重复写
    private UploadPolicyDTO buildValidPolicy() {
        UploadPolicyDTO dto = new UploadPolicyDTO();
        dto.setBucket("mall-product");
        dto.setBusinessType("spu");
        dto.setContentType("image/jpeg");
        dto.setFileSize(1_048_576L);
        return dto;
    }
}
```

### 关键设计点

| 点 | 说明 |
|----|------|
| `@Nested` 分组 | 按业务方法分组（签发凭证/回调/删除/下载），报告清晰 |
| `@BeforeEach` 准备 | 统一初始化 Service 和 Mock，避免每个测试重复 |
| `@AfterEach` 清理 | 清理 `UserContext` ThreadLocal，防止测试间互相污染 |
| 正常 + 异常 + 边界 | 每个方法都覆盖正常流程、异常场景、边界值 |
| 辅助方法 | `buildValidPolicy()` 构造测试数据，减少重复 |
| `lenient()` | 共享桩但不是每个测试都用到的，用 `lenient()` 避免警告 |

---

## 八、核心知识点速查

| 知识点 | 一句话 |
|--------|--------|
| 单元测试 | 测单个类，Mock 所有依赖，只验证业务逻辑 |
| `@Test` + `@DisplayName` | 标记测试方法 + 中文描述 |
| `@Nested` | 按方法/场景分组，报告更清晰 |
| `@ParameterizedTest` | 一组数据测一个逻辑，避免复制粘贴 |
| `@Mock` / `@InjectMocks` | 造假对象 / 把假对象注入被测类 |
| `when().thenReturn()` | 打桩——控制 Mock 方法返回什么 |
| `verify()` | 验证方法是否被调用、调用几次 |
| `assertThat()` | AssertJ 流式断言，本项目统一用这个 |
| `assertThatThrownBy()` | 断言抛出异常的类型和属性 |
| Given-When-Then | 三段式结构，Given 必须独立 |
| 构造器注入 | 可测试性的基础，不用 `@Autowired` 字段注入 |

### 常用 import 速查

```java
// JUnit 5
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Mockito
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

// AssertJ
import static org.assertj.core.api.Assertions.*;
```

---

## 下一步

本篇讲了单元测试——测 Service 层业务逻辑。但 Controller 层（HTTP 路由、参数校验、响应序列化）用单元测试不够，需要**切片测试**。

下一篇 [03-slice-testing.md](./03-slice-testing.md) 讲 `@WebMvcTest` 怎么只加载 Controller 层、MockMvc 怎么模拟 HTTP 请求。
