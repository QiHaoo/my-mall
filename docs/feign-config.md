# 远程调用（Feign）配置说明

> 紧贴项目，记录 OpenFeign + LoadBalancer 在 my-mall 中的实际配置与用法。

---

## 1. 依赖

所有远程调用相关依赖统一在 `mall-common` 中声明，子模块只需依赖 `mall-common` 即可自动传递。

| 依赖 | 作用 |
|------|------|
| `spring-cloud-starter-openfeign` | Feign 声明式 HTTP 客户端 |
| `spring-cloud-starter-loadbalancer` | 客户端负载均衡（替代已废弃的 Ribbon） |
| `spring-cloud-starter-alibaba-nacos-discovery` | Nacos 服务发现，Feign 通过服务名找到目标地址 |

> **注意**：`LoadBalancer` 必须和 `OpenFeign` 一起引入，Feign 默认会通过 LoadBalancer 解析服务名。如果只引 OpenFeign 不引 LoadBalancer，会报 `UnknownHostException`。

---

## 2. Feign 客户端接口规范

### 2.1 位置

远程调用接口统一放在 **`feign`** 包下，命名以 `FeignClient` 结尾：

```
com.mymall.{module}/
├── controller/          # 本地 REST 接口（Provider）
├── feign/               # 远程调用接口（Consumer 声明）
│   └── ××FeignClient.java
```

### 2.2 写法

```java
@Component
@FeignClient(name = "mall-xxx", path = "/module/resource")
public interface XxxFeignClient {

    @GetMapping("/list")
    R list();

    @GetMapping("/{id}")
    R getById(@PathVariable Long id);
}
```

**参数说明：**

| 参数 | 说明 |
|------|------|
| `name` | 目标服务在 Nacos 中注册的 `spring.application.name` |
| `path` | 目标 Controller 的 `@RequestMapping` 前缀，必须带模块名（如 `/coupon/coupon`） |
| 方法映射 | 与目标 Controller 的方法签名保持一致（路径、参数、返回值类型） |

> **不需要 `@RequestMapping` 在接口类上**，Feign 只用 `path` 参数定义前缀。

### 2.3 当前项目中已有的 Feign 客户端

| 模块 | FeignClient | 目标服务 | 目标接口 |
|------|-----------|---------|---------|
| mall-member | `CouponFeignClient` | `mall-coupon` | `GET /coupon/coupon/list` |

---

## 3. Provider 端（被调用方）

Provider 端不需要任何 Feign 相关注解，就是一个普通的 REST Controller：

```java
// mall-coupon 模块
@RestController
@RequestMapping("/coupon/coupon")
public class CouponController {

    @GetMapping("/list")
    public R list() {
        return R.ok().put("coupons", list).put("total", list.size());
    }
}
```

> Feign 框架层面没有 "Provider 注册" 的概念 —— 服务通过 Nacos Discovery 注册后，Feign 就能通过服务名找到它。

---

## 4. Consumer 端（调用方）

### 4.1 启动类添加 @EnableFeignClients

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients   // ← 必须加，否则 @FeignClient 不会生效
public class MemberApplication { ... }
```

### 4.2 Controller 中注入使用

```java
@RestController
@RequestMapping("/member/member")
@RequiredArgsConstructor
public class MemberController {

    private final CouponFeignClient couponFeignClient;

    @GetMapping("/test-remote")
    public R testRemote() {
        return couponFeignClient.list(); // 就像调用本地方法
    }
}
```

---

## 5. 调用链路

```
member 服务                          coupon 服务
    │                                    │
    ├─ controller 调用 feignClient.list()
    ├─ Feign 拦截，发 HTTP 请求
    ├─ LoadBalancer 从 Nacos 获取 mall-coupon 的实例列表
    ├─ 选一个实例 → http://{ip}:{port}/coupon/coupon/list
    │                                    │
    │    ────────── HTTP GET ──────────►  ├─ CouponController.list()
    │    ◄───────── JSON 响应 ──────────  ├─ 返回 R
    │                                    │
    ├─ Feign 反序列化 JSON → R 对象
    └─ controller 返回给前端
```

---

## 6. 测试中覆盖 Feign URL

集成测试时，需要绕过 Nacos 和 LoadBalancer，让 Feign 直接调用 WireMock 的地址：

```java
// 在 @SpringBootTest 中用 @DynamicPropertySource 动态设置
@DynamicPropertySource
static void configureFeign(DynamicPropertyRegistry registry, WireMockRuntimeInfo wm) {
    registry.add("spring.cloud.openfeign.client.config.mall-coupon.url",
            () -> "http://localhost:" + wm.getHttpPort());
}
```

其他测试用属性：

```properties
spring.cloud.nacos.discovery.enabled=false   # 不需要 Nacos
spring.cloud.loadbalancer.enabled=false      # 不需要负载均衡
```

---

## 7. 完整配置示例（mall-member）

**application.yml**

```yaml
server:
  port: 7100

spring:
  application:
    name: mall-member          # ← Feign 用这个 name 从 Nacos 发现本服务
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
```

**pom.xml（关键依赖）**

```xml
<!-- 以下三个依赖缺一不可 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
```

---

## 8. 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| `UnknownHostException: mall-coupon` | 没有引入 LoadBalancer | 加 `spring-cloud-starter-loadbalancer` |
| Feign 调用返回 404 | `path` 参数与目标 Controller 路径不匹配 | 检查 `@RequestMapping` 路径是否带模块前缀 |
| `@FeignClient` 不生效 | 没加 `@EnableFeignClients` | 在启动类上加注解 |
| 返回数据为 null | 目标返回 JSON 字段名与 Feign 接口返回值类型不匹配 | 确保 DTO 字段名一致，或用 `@JsonProperty` |
