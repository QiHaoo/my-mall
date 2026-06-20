# 端到端测试方案对比

> 对比微服务端到端测试的三种主流方案，供后续选型参考。

---

## 一、方案概览

| 维度 | Testcontainers | Spring Cloud Contract | WireMock 集成测试 |
|------|:---:|:---:|:---:|
| 真实服务 | ✅ 真实启动 | ❌ 契约模拟 | ❌ HTTP 模拟 |
| 需要 Docker | ✅ | ❌ | ❌ |
| 启动速度 | 慢（5~30s） | 快 | 快 |
| CI 友好 | 中等 | ✅ | ✅ |
| 学习曲线 | 低 | 中高 | 低 |
| 适用阶段 | 中后期 | 中后期 | 全程 |

---

## 二、方案一：Testcontainers（真实 E2E）

### 原理

通过 Docker API 自动拉取镜像、启动容器、注入连接信息、测试完自动销毁。无需手动操作任何外部服务。

```
运行测试 → Testcontainers docker run nacos
         → Testcontainers docker run coupon 服务
         → 注入真实连接地址到测试上下文
         → member 通过 Nacos 发现 coupon → 真实 HTTP 调用
         → 测试结束，自动 docker stop / rm
```

### 依赖

```xml
<!-- mall-member/pom.xml，test scope -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### 示例代码

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
class MemberE2ETest {

    // 启动 Nacos 容器（会自动注入 spring.cloud.nacos.discovery.server-addr）
    @Container
    @ServiceConnection
    static GenericContainer<?> nacos = new GenericContainer<>(
            DockerImageName.parse("nacos/nacos-server:v2.4.0"))
            .withEnv("MODE", "standalone")
            .withExposedPorts(8848);

    // coupon 服务也需要作为容器启动，或在本机先启动
    // 此处省略 coupon 容器的配置...

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCallRealCouponService() throws Exception {
        mockMvc.perform(get("/member/test-remote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coupons").isArray());
    }
}
```

### 前置条件

- 机器上 Docker 正在运行（你的 WSL2 已有 ✅）
- 首次运行需拉取镜像（约 1 分钟，仅一次）
- coupon 服务需打成镜像或在 test 中动态构建

### 优缺点

- ✅ 最接近生产：走真实 Nacos 注册 → Feign 调用 → 真实网络
- ✅ 全自动：不需要手动 `docker-compose up`
- ❌ 启动慢：每次测试都要等容器就绪（5~30s）
- ❌ 资源占用：同时跑 Nacos + coupon + member 三个 JVM
- ❌ coupon 服务需要能独立启动（依赖 DB 等），测试配置复杂

---

## 三、方案二：Spring Cloud Contract（契约测试）

### 原理

Provider（coupon）发布 API 契约文件 → Consumer（member）用契约生成 stub → 测试时 WireMock 按契约响应。双方独立测试，不启动对方服务，但契约保证兼容性。

### 工作流

```
coupon 模块                        member 模块
    │                                  │
    ├─ 写契约（Groovy/YAML）            │
    ├─ mvn install 发布契约 jar ──────► ├─ 依赖契约 jar
    │                                  ├─ @AutoConfigureStubRunner 启动 stub
    │                                  └─ 测试验证
```

### 优缺点

- ✅ 不启动真实服务，速度快
- ✅ Provider 改 API 时 Consumer 测试立即发现不兼容
- ❌ 搭建成本高：需要契约仓库 + Groovy DSL + Maven 插件
- ❌ 学习曲线陡，契约语法较复杂
- ❌ 只验证 API 签名兼容，不验证真实网络行为

---

## 四、方案三：WireMock 集成测试（当前已实现）

### 原理

在测试中启动内嵌 WireMock HTTP 服务器，模拟远程服务的 HTTP 响应。Feign 发出的 HTTP 请求被 WireMock 拦截并返回预设响应。

```
测试 → Controller → FeignClient → HTTP 请求 → WireMock（拦截，返回 stub 数据）
                                                              ↑
                                          不经过 Nacos / LoadBalancer
```

### 示例（当前项目已实现）

```java
@SpringBootTest
@WireMockTest(httpPort = 0)
class MemberControllerIT {

    @DynamicPropertySource
    static void configureFeign(DynamicPropertyRegistry r, WireMockRuntimeInfo wm) {
        // 将 Feign URL 指向 WireMock，绕过 Nacos 和 LoadBalancer
        r.add("spring.cloud.openfeign.client.config.mall-coupon.url",
              () -> "http://localhost:" + wm.getHttpPort());
    }

    @BeforeEach
    void stub() {
        stubFor(get("/coupon/list").willReturn(okJson("""
            {"code":200,"msg":"success","data":{"coupons":[...]}}
        """)));
    }

    @Test
    void test() {
        // 验证 Feign 序列化 → HTTP 传输 → 反序列化 全链路
        mockMvc.perform(get("/member/test-remote"))
                .andExpect(jsonPath("$.data.coupons").isArray());
        // 确认 Feign 确实发出了 HTTP 请求
        verify(getRequestedFor(urlEqualTo("/coupon/list")));
    }
}
```

### 优缺点

- ✅ 最轻量：不需要 Docker、不需要启动其他服务
- ✅ 速度快：和普通单元测试一样快
- ✅ 覆盖 Feign 全链路：序列化 / 路由 / HTTP / 反序列化 全部验证
- ❌ 不验证 Nacos 服务发现是否正常工作
- ❌ 不验证真实服务的行为逻辑

---

## 五、选型建议

### 当前阶段（学习 + 开发初期）

**继续用方案三（WireMock）+ 手动联调**：

```
开发时     → 启动 coupon + member，curl 验证（手动联调）
提交前     → 跑 @WebMvcTest 单元测试 + WireMock 集成测试
CI 阶段    → 同上，纯 JVM 测试，不依赖 Docker
```

### 中期（服务增多后）

**引入方案一（Testcontainers）**，仅在关键链路使用：

```
CI 夜间构建 → Testcontainers 启动全套中间件 + 所有服务 → 全链路 E2E
日常提交     → 仍用 WireMock（保持快速反馈）
```

### 后期（上线前）

**视情况引入方案二（Spring Cloud Contract）**，但学习成本较高，非必须。

---

## 六、当前项目测试分层

```
┌─────────────────────────────────────────┐
│  手动联调（开发阶段最主要）               │  ← 启动真实服务，curl/浏览器验证
├─────────────────────────────────────────┤
│  E2E 测试（Testcontainers，待引入）       │  ← 自动化真实链路
├─────────────────────────────────────────┤
│  集成测试（WireMock，✅ 已实现）          │  ← Feign HTTP 层全链路
├─────────────────────────────────────────┤
│  单元测试（@WebMvcTest，✅ 已实现）       │  ← Controller 逻辑
└─────────────────────────────────────────┘
```
