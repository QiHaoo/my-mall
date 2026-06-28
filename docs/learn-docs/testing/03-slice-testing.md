# 03 · 切片测试

> 切片测试夹在单元测试和集成测试之间——比单元测试重（要启动部分 Spring），比集成测试轻（只启动一层）。本篇讲 `@WebMvcTest` 怎么验证 Controller 层。
>
> 前置阅读：[02-unit-testing.md](./02-unit-testing.md)

---

## 一、为什么需要切片测试

上一篇的单元测试测的是 Service 层，把 Mapper 和 Feign Client 全 Mock 了。但 Controller 层有些东西单元测试测不到：

| Controller 的职责 | 单元测试能测吗 | 切片测试能测吗 |
|------------------|:---:|:---:|
| URL 路由对不对（`/category/list/tree` 能匹配到方法） | ❌ | ✅ |
| `@Valid` 参数校验有没有生效（缺必填字段返回 400） | ❌ | ✅ |
| 返回的 JSON 格式对不对（字段名、嵌套结构） | ❌ | ✅ |
| `@ExceptionHandler` 全局异常处理工作不工作 | ❌ | ✅ |
| Service 调用对不对 | ✅（Mock Service） | ✅（Mock Service） |

**单元测试测不了 HTTP 层**，因为它不启动 Spring MVC，没有路由、没有参数校验、没有 JSON 序列化。

但如果为了测 Controller 启动整个 Spring 应用（`@SpringBootTest`），又太重了——要把所有 Bean 都加载，包括数据库连接、Nacos 配置、Feign Client 等等，启动慢还容易因为环境问题失败。

**切片测试就是折中**：只启动 Controller 这一层相关的组件（Spring MVC、参数校验、JSON 序列化、全局异常处理），其余全 Mock。

---

## 二、@WebMvcTest 加载了什么

```
@WebMvcTest(CategoryController.class)
  ↓
只加载：
  ├── CategoryController（你指定的 Controller）
  ├── Spring MVC 核心组件（DispatcherServlet、HandlerMapping）
  ├── 参数校验器（@Valid / @Validated → Validator）
  ├── JSON 序列化（Jackson ObjectMapper → 包括 JacksonConfig 的 Long→String）
  └── 全局异常处理器（@ControllerAdvice → GlobalExceptionHandler）

不加载：
  ├── CategoryService（需要你用 @MockitoBean Mock）
  ├── CategoryMapper（不加载，因为 Service 被 Mock 了）
  ├── 数据源 / Nacos / Feign（都不加载）
  └── 其他 Controller（只加载你指定的那个）
```

启动时间约 2 秒（比 `@SpringBootTest` 的 5~10 秒快很多），因为只初始化 MVC 这一层。

---

## 三、@MockitoBean：Mock Controller 的依赖

Controller 依赖 Service，但切片测试不加载 Service，需要手动 Mock。

### 3.1 @MockitoBean vs @MockBean

Spring Boot 3.4 之前用 `@MockBean`，3.4 之后**废弃了**，改用 `@MockitoBean`：

```java
// ❌ Spring Boot 3.4 已废弃
@MockBean
private CategoryService categoryService;

// ✅ Spring Boot 3.4+ 推荐
@MockitoBean
private CategoryService categoryService;
```

> 项目用 Spring Boot 3.4，统一用 `@MockitoBean`。它和 `@MockBean` 功能一样，只是换了名字（Spring Boot 把 Mock 相关的注解从 spring-test 移到了 spring-boot-test，统一命名规范）。

### 3.2 @MockitoBean vs @Mock 的区别

| 注解 | 用在哪 | 区别 |
|------|--------|------|
| `@Mock` | 纯单元测试（`@ExtendWith(MockitoExtension.class)`） | 造一个纯 Mock 对象，不在 Spring 容器里 |
| `@MockitoBean` | 切片测试 / 集成测试（有 Spring 上下文） | 造一个 Mock 对象，**替换 Spring 容器里的同名 Bean** |

切片测试启动了 Spring 上下文，Controller 里的 `@Autowired CategoryService` 需要从容器里拿 Bean。`@MockitoBean` 就是往容器里塞一个 Mock 的 Service，Controller 拿到的就是假的。

---

## 四、MockMvc：模拟 HTTP 请求

切片测试用 `MockMvc` 发请求，不真正启动 HTTP 服务器，而是在内存里模拟 Spring MVC 的请求处理流程：

```java
@WebMvcTest(CategoryController.class)
@ActiveProfiles("test")
@DisplayName("分类管理 Controller")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;          // Spring 自动注入

    @MockitoBean
    private CategoryService categoryService;  // Mock Service

    @Test
    @DisplayName("GET /category/list/tree 应返回三级分类树")
    void shouldReturnCategoryTree() throws Exception {
        // Given: Service 返回的假数据
        when(categoryService.listTree()).thenReturn(List.of(
                buildCategoryVO(1L, "电子产品", List.of(
                        buildCategoryVO(11L, "手机", List.of())
                ))
        ));

        // When & Then: 发请求 + 断言响应
        mockMvc.perform(get("/category/list/tree"))           // 发 GET 请求
                .andExpect(status().isOk())                   // 断言 HTTP 200
                .andExpect(jsonPath("$.code").value(200))     // 断言 JSON 的 code 字段
                .andExpect(jsonPath("$.data").isArray())      // 断言 data 是数组
                .andExpect(jsonPath("$.data[0].name").value("电子产品"));
    }
}
```

### 4.1 发请求

```java
// GET 请求
mockMvc.perform(get("/category/list/tree"))
mockMvc.perform(get("/category/{id}", 1L))

// POST 请求 + JSON body
mockMvc.perform(post("/category/save")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"新分类\",\"parentId\":0}"))

// 带请求头
mockMvc.perform(get("/category/list/tree")
        .header("Authorization", "Bearer token-xxx"))
```

### 4.2 断言响应

```java
mockMvc.perform(get("/category/list/tree"))
        // HTTP 状态码
        .andExpect(status().isOk())                    // 200
        .andExpect(status().isBadRequest())            // 400

        // JSON 路径断言（用 jsonPath）
        .andExpect(jsonPath("$.code").value(200))      // $.code = 200
        .andExpect(jsonPath("$.msg").value("success"))
        .andExpect(jsonPath("$.data").isArray())       // data 是数组
        .andExpect(jsonPath("$.data[0].name").value("电子产品"))
        .andExpect(jsonPath("$.data[*].name",                           // 所有元素的 name 字段
                hasItems("电子产品", "服装")))                            // 包含这些值
        .andExpect(jsonPath("$.data.length()").value(3))               // 数组长度

        // 响应头
        .andExpect(header().string("Content-Type", "application/json"));
```

### 4.3 jsonPath 语法速查

`jsonPath` 用点号 `.` 访问嵌套字段，用 `[index]` 访问数组元素：

| 表达式 | 含义 |
|--------|------|
| `$.code` | 根对象的 code 字段 |
| `$.data` | 根对象的 data 字段 |
| `$.data[0]` | data 数组的第一个元素 |
| `$.data[0].name` | 第一个元素的 name 字段 |
| `$.data[*].name` | data 数组所有元素的 name |
| `$.data.length()` | data 数组的长度 |
| `$.data[?(@.status==1)]` | data 中 status==1 的元素（过滤） |

> `$` 代表 JSON 根对象。本项目统一返回 `R<T>` 结构（`{code, msg, data}`），所以断言通常从 `$.code` 和 `$.data` 开始。

---

## 五、必须覆盖的四个场景

规范要求 Controller 测试必须覆盖这四种场景：

| 场景 | 怎么测 | 验证什么 |
|------|--------|---------|
| 正常请求 | 合法参数 → 调用 | 200 + 正确数据 |
| 参数校验失败 | 缺必填字段 / 格式错 | 400 错误码 |
| 业务异常 | Service 抛 `BizException` | 对应错误码 |
| 空结果 | 查询无数据 | 200 + 空列表 |

### 5.1 参数校验失败

这是切片测试的核心价值——单元测试测不了参数校验：

```java
@Test
@DisplayName("新增分类时 name 为空应返回参数校验错误")
void shouldReturn400WhenNameIsBlank() throws Exception {
    // Given: name 为空的请求体
    String json = "{\"name\":\"\",\"parentId\":0}";

    // When & Then: 应返回参数校验失败
    mockMvc.perform(post("/category/save")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isOk())                          // 本项目统一 HTTP 200
            .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));  // 错误码
}
```

> **注意本项目的特殊约定**：HTTP 状态码统一返回 200，业务成功/失败用 `R.code` 区分（200 成功，其他失败）。所以参数校验失败时 HTTP 是 200，但 `$.code` 是参数错误码。这是 `GlobalExceptionHandler` 统一处理的，切片测试正好能验证这个链路。

### 5.2 业务异常

```java
@Test
@DisplayName("查询不存在的分类应返回业务异常")
void shouldReturnBizErrorWhenNotFound() throws Exception {
    // Given: Service 抛业务异常
    when(categoryService.getById(999L))
            .thenThrow(new BizException(ResultCode.CATEGORY_NOT_FOUND));

    // When & Then
    mockMvc.perform(get("/category/info/{id}", 999L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultCode.CATEGORY_NOT_FOUND.getCode()));
}
```

这个测试同时验证了：Controller 调用 Service → Service 抛异常 → `GlobalExceptionHandler` 捕获 → 返回正确错误码。整条链路在切片测试里都能覆盖。

---

## 六、完整实战：参照项目真实测试

看项目的 [CategoryControllerTest.java](../../../mall-product/src/test/java/com/mymall/product/controller/CategoryControllerTest.java) 结构：

```java
@WebMvcTest(CategoryController.class)
@ActiveProfiles("test")
@DisplayName("分类管理 Controller")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Nested
    @DisplayName("查询分类树")
    class ListTree {

        @Test
        @DisplayName("应返回三级分类树")
        void shouldReturnTree() throws Exception {
            // Given
            when(categoryService.listTree()).thenReturn(buildMockTree());

            // When & Then
            mockMvc.perform(get("/category/list/tree"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].children").isNotEmpty());
        }

        @Test
        @DisplayName("无数据时应返回空数组")
        void shouldReturnEmptyWhenNoData() throws Exception {
            when(categoryService.listTree()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/category/list/tree"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Nested
    @DisplayName("新增分类")
    class Save {

        @Test
        @DisplayName("合法参数应创建成功")
        void shouldSaveWhenValid() throws Exception {
            when(categoryService.save(any(CategorySaveDTO.class))).thenReturn(1L);

            mockMvc.perform(post("/category/save")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"新分类\",\"parentId\":0,\"sort\":0,\"icon\":\"\",\"showStatus\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("name 为空应返回参数校验错误")
        void shouldReturnErrorWhenNameBlank() throws Exception {
            mockMvc.perform(post("/category/save")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\",\"parentId\":0}"))
                    .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
        }
    }
}
```

---

## 七、@ActiveProfiles("test") 的作用

切片测试类上通常会加 `@ActiveProfiles("test")`，作用是激活 `application-test.yml` 配置：

```java
@WebMvcTest(CategoryController.class)
@ActiveProfiles("test")   // 加载 application-test.yml 而不是 application.yml
class CategoryControllerTest { ... }
```

`application-test.yml` 里禁用了 Nacos、Sentinel 等中间件，避免测试时去连真实中间件：

```yaml
# src/test/resources/application-test.yml
spring:
  cloud:
    nacos:
      discovery:
        enabled: false    # 切片测试不需要服务发现
      config:
        enabled: false    # 不连配置中心
    sentinel:
      enabled: false
```

> 如果不加 `@ActiveProfiles("test")`，测试会加载 `application.yml`，可能尝试连 Nacos 导致启动失败或超时。

---

## 八、切片测试 vs 单元测试 vs 集成测试

| 维度 | 单元测试 | 切片测试（本篇） | 集成测试（下篇） |
|------|---------|----------------|----------------|
| 注解 | 无（纯 JUnit） | `@WebMvcTest` | `@SpringBootTest` |
| 启动 Spring | ❌ | 部分启动（MVC 层） | 完整启动 |
| 测什么 | Service 业务逻辑 | Controller HTTP 层 | 跨层/跨服务链路 |
| Service | `@Mock` Mock | `@MockitoBean` Mock | 真实 Bean |
| 数据库 | Mock | 不涉及 | H2 内存库 |
| 远程服务 | Mock | 不涉及 | WireMock |
| 速度 | <1s | ~2s | ~5s |
| 类后缀 | `*Test` | `*Test` | `*IT` |
| 执行插件 | surefire | surefire | failsafe |

**记忆要点**：
- 测 Service 用单元测试（`@Mock`，不启动 Spring）
- 测 Controller 用切片测试（`@MockitoBean`，启动 MVC 层）
- 测跨服务链路用集成测试（WireMock，启动完整上下文）

---

## 九、核心知识点速查

| 知识点 | 一句话 |
|--------|--------|
| 切片测试 | 只启动某一层（Controller），其余 Mock，比单元测试重、比集成测试轻 |
| `@WebMvcTest(XxxController.class)` | 只加载指定 Controller + MVC 组件（校验器/序列化/异常处理） |
| `@MockitoBean` | Mock 一个 Bean 替换 Spring 容器里的同名 Bean（Spring Boot 3.4+，替代废弃的 `@MockBean`） |
| `MockMvc` | 在内存里模拟 HTTP 请求，不启动真实 HTTP 服务器 |
| `mockMvc.perform(get(url))` | 发 GET 请求 |
| `jsonPath("$.code")` | 断言 JSON 路径的值 |
| `@ActiveProfiles("test")` | 激活测试配置，禁用 Nacos 等中间件 |
| 四场景 | 正常请求 + 参数校验失败 + 业务异常 + 空结果，必须覆盖 |

---

## 下一步

切片测试验证了 Controller 层。但当一个服务通过 Feign 调用另一个服务时，切片测试测不到这条跨服务链路——需要**集成测试**用 WireMock 模拟远程服务。

下一篇 [04-integration-testing.md](./04-integration-testing.md) 讲 WireMock 怎么拦截 Feign 的 HTTP 请求、怎么验证跨服务全链路。
