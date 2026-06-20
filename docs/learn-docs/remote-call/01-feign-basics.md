# 远程调用基础

## 1. 什么是远程调用

微服务架构下，一个服务需要调用另一个服务的接口 —— 这就是远程调用（Remote Procedure Call，RPC）。

```
┌──────────┐      HTTP       ┌──────────┐
│  mall-   │ ──────────────► │  mall-   │
│  member  │ ◄────────────── │  coupon  │
└──────────┘      JSON       └──────────┘
```

## 2. 传统方式：RestTemplate

在没有 Feign 之前，Spring 中发起 HTTP 调用需要手动拼接 URL、设置 Header、序列化请求体：

```java
// ❌ 繁琐、重复、容易出错
String url = "http://mall-coupon/coupon/coupon/list";
ResponseEntity<R> response = restTemplate.getForEntity(url, R.class);
```

**痛点：**
- URL 硬编码，服务地址变更需要全局搜索替换
- 参数拼接容易出错
- 没有类型安全，返回值需要手动强转
- 每次调用都要写一堆样板代码

## 3. Feign：声明式 HTTP 客户端

Feign 的核心思想：**把远程 HTTP 接口声明为一个 Java 接口**，调用远程服务就像调用本地方法。

```java
// ✅ 简洁、类型安全
@FeignClient(name = "mall-coupon", path = "/coupon/coupon")
public interface CouponFeignClient {

    @GetMapping("/list")
    R list();  // 调用 couponFeignClient.list() 等价于发 HTTP GET
}
```

## 4. 声明式 vs 命令式

| | 命令式（RestTemplate） | 声明式（Feign） |
|------|------|------|
| 怎么写 | 告诉怎么做（拼接 URL、发请求） | 告诉要什么（定义接口） |
| URL 管理 | 分散在代码各处 | 集中在接口注解中 |
| 参数传递 | 手动拼接字符串 | 方法参数映射 |
| 类型安全 | 无 | 编译期检查 |
| 可读性 | 差 | 好 |

## 5. Feign 能做什么

- 自动将方法调用转为 HTTP 请求
- 自动序列化参数（对象 → JSON）
- 自动反序列化响应（JSON → 对象）
- 与 Spring MVC 注解兼容（`@GetMapping` / `@PostMapping` / `@PathVariable` 等）
- 集成负载均衡（通过服务名自动发现实例）
- 集成熔断降级（配合 Sentinel / Resilience4j）

## 6. 常见远程调用框架对比

| | Feign | Dubbo | gRPC | RestTemplate |
|------|------|------|------|------|
| 协议 | HTTP | TCP | HTTP/2 | HTTP |
| 序列化 | JSON | Hessian/JSON | Protobuf | JSON |
| 跨语言 | ✅ | ❌ | ✅ | ✅ |
| 生态 | Spring Cloud 标配 | 阿里系 | Google | Spring 内置 |
| 性能 | 中等 | 高 | 高 | 中等 |
