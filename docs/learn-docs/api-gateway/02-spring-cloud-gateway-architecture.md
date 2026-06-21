# Spring Cloud Gateway 架构

## 1. 为什么是响应式

Spring Cloud Gateway 基于 **WebFlux + Netty**，而非传统的 Servlet 模型：

```
传统 Servlet（Zuul 1.x / Spring MVC）       响应式（Spring Cloud Gateway）
┌─────────────────────────────────┐    ┌─────────────────────────────────┐
│  每个请求一个线程（线程池）        │    │  少量线程处理大量连接（EventLoop） │
│  I/O 操作阻塞线程                │    │  I/O 操作不阻塞线程              │
│  高并发时线程池耗尽               │    │  高并发时依然高效                │
└─────────────────────────────────┘    └─────────────────────────────────┘
```

**优势**：
- 网关是 I/O 密集型（转发请求），响应式模型更适合
- 单机可处理数万并发连接
- 背压（Backpressure）支持，下游慢时不会压垮系统

**限制**：
- 不能使用 Servlet API、Spring MVC
- 数据库访问需要响应式驱动（R2DBC）或 WebClient
- 调试比同步代码复杂

## 2. 核心三要素

```
请求 → Route（路由）
         │
         ├── Predicate（断言）：匹配条件，决定是否走这条路由
         │
         ├── Filter（过滤器）：请求/响应处理逻辑
         │
         └── URI：目标服务地址
```

### 2.1 Route（路由）

路由是网关的核心单元，由 ID + Predicate + Filter + URI 组成：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: product-route          # 路由 ID（唯一）
          uri: lb://mall-product     # 目标服务（lb:// 表示负载均衡）
          predicates:                # 匹配条件
            - Path=/api/product/**
          filters:                   # 过滤器
            - StripPrefix=1
```

### 2.2 Predicate（断言/路由谓词）

定义请求匹配规则，返回 `true` 表示匹配：

```
请求 ──► Predicate 链 ──► 全部 true? ──► 走这条路由
                            │ false?  ──► 尝试下一条路由
```

支持 AND 组合（同一路由下多个 Predicate 需全部匹配）。

### 2.3 Filter（过滤器）

对请求/响应进行处理，按作用范围分为两种：

| 类型 | 作用范围 | 使用方式 |
|------|---------|---------|
| **GatewayFilter** | 单个路由 | 配置在 `filters` 下 |
| **GlobalFilter** | 所有路由 | 实现 `GlobalFilter` 接口 |

按执行顺序：

```
请求进入
    │
    ▼
Pre Filter（前置过滤器）
    │
    ▼
转发到下游服务
    │
    ▼
Post Filter（后置过滤器）
    │
    ▼
返回响应
```

## 3. 请求处理流程

```
客户端请求
    │
    ▼
┌─────────────────────┐
│  HandlerMapping     │  根据 Predicate 匹配 Route
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  Pre GlobalFilter   │  全局前置（鉴权、日志）
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  Pre GatewayFilter  │  路由级前置（请求改写、限流）
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  转发到下游服务       │  Netty Routing Filter
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  Post GatewayFilter │  路由级后置（响应改写）
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  Post GlobalFilter  │  全局后置（指标统计）
└──────────┬──────────┘
           │
           ▼
      返回响应
```

## 4. 服务发现集成

### 4.1 自动路由发现

开启后，网关自动为 Nacos 中注册的每个服务创建路由：

```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true                # 开启自动路由
          lower-case-service-id: true  # 服务名小写
```

访问规则：`http://网关:端口/服务名/**` → 转发到对应服务

```
GET /mall-product/product/list  →  mall-product 服务
GET /mall-order/order/detail    →  mall-order 服务
```

### 4.2 手动路由 + lb:// 协议

更精细控制，手动配置路由规则：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: product-route
          uri: lb://mall-product      # lb:// = LoadBalancer + 服务名
          predicates:
            - Path=/api/product/**
          filters:
            - StripPrefix=1           # 转发时去掉 /api/product 前缀
```

`lb://` 协议工作流程：
1. 从 Nacos 查询 `mall-product` 的所有实例
2. Spring Cloud LoadBalancer 选择一个实例
3. 将请求转发到该实例的对应端口

### 4.3 自动路由 vs 手动路由

| 方式 | 配置量 | 灵活性 | 适用场景 |
|------|--------|--------|---------|
| 自动路由 | 零配置 | 低（路径固定） | 开发/测试环境快速验证 |
| 手动路由 | 需配置 | 高（自定义前缀/过滤器） | 生产环境 |

## 5. 依赖关系

```
spring-cloud-starter-gateway
    ├── spring-webflux          （响应式 Web 框架）
    ├── spring-cloud-context    （配置刷新）
    └── netty                   （NIO 服务器）

spring-cloud-starter-alibaba-nacos-discovery
    └── 服务发现（lb:// 协议解析）

spring-cloud-starter-loadbalancer
    └── 客户端负载均衡（替代已废弃的 Ribbon）

spring-cloud-alibaba-sentinel-gateway
    └── 网关级限流（与 Sentinel 集成）
```

## 6. 与 Spring MVC 的区别

| 维度 | Spring Cloud Gateway | Spring MVC |
|------|---------------------|-----------|
| 底层 | WebFlux + Netty | Servlet + Tomcat |
| 编程模型 | 响应式（Mono/Flux） | 同步阻塞 |
| 依赖 | `spring-cloud-starter-gateway` | `spring-boot-starter-web` |
| 共存 | 不能与 spring-boot-starter-web 共存 | 不能与 gateway 共存 |

> **重要**：网关模块不能引入 `spring-boot-starter-web`，否则会冲突。

## 7. 配置方式

### 7.1 YAML 配置（推荐入门）

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: my-route
          uri: lb://my-service
          predicates:
            - Path=/api/**
          filters:
            - StripPrefix=1
```

### 7.2 Java 代码配置

```java
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("product-route", r -> r
                        .path("/api/product/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://mall-product"))
                .build();
    }
}
```

### 7.3 Nacos 动态路由

路由配置从 Nacos 配置中心拉取，支持运行时动态修改：

```yaml
# Nacos 配置：mall-gateway.yaml
spring:
  cloud:
    gateway:
      routes:
        - id: product-route
          uri: lb://mall-product
          predicates:
            - Path=/api/product/**
          filters:
            - StripPrefix=1
```

## 8. 下一步

- [路由与过滤器详解](./03-route-predicate-filter.md)
- [生产实践](./04-production-practices.md)
