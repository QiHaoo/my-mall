# API 网关配置指南

## 1. 模块概述

| 项目 | 说明 |
|------|------|
| 模块名 | `mall-gateway` |
| 端口 | `1000` |
| 框架 | Spring Cloud Gateway 4.x（WebFlux + Netty） |
| 注册中心 | Nacos |
| 配置中心 | Nacos（`mall-gateway.yaml`，`refreshEnabled=true`） |
| 包名 | `com.mymall.gateway` |

> **注意**：Gateway 基于 WebFlux，不能引入 `spring-boot-starter-web`，不能使用 `@MapperScan`。

---

## 2. 服务注册配置

### 2.1 application.yml

```yaml
spring:
  application:
    name: mall-gateway
  config:
    import:
      - "nacos:mall-gateway.yaml?refreshEnabled=true"
  autoconfigure:
    exclude:
      - io.seata.spring.boot.autoconfigure.SeataAutoConfiguration
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
        group: DEFAULT_GROUP
        username: nacos
        password: nacos
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
        username: nacos
        password: nacos
```

### 2.2 配置中心 Data ID

| Data ID | 用途 | 内容示例 |
|---------|------|---------|
| `mall-gateway.yaml` | 网关配置（路由、过滤器） | 动态路由规则、Sentinel 限流规则 |

> 生产环境建议将路由规则放到 Nacos 配置中心，实现动态增删改。

---

## 3. 路由配置总览

### 3.1 负载均衡路由（转发到微服务）

| 路由 ID | 路径 | 目标服务 | StripPrefix |
|---------|------|---------|-------------|
| `product-route` | `/api/product/**` | `lb://mall-product` | 1 |
| `member-route` | `/api/member/**` | `lb://mall-member` | 1 |
| `coupon-route` | `/api/coupon/**` | `lb://mall-coupon` | 1 |
| `order-route` | `/api/order/**` | `lb://mall-order` | 1 |
| `ware-route` | `/api/ware/**` | `lb://mall-ware` | 1 |

**StripPrefix=1** 说明：请求 `/api/product/list` 转发到 `mall-product` 时去掉 `/api` 前缀，实际请求变为 `/product/list`。

### 3.2 外部 URL 路由（转发到外部网站）

| 路由 ID | 路径 | 目标 | 用途 |
|---------|------|------|------|
| `baidu-route` | `/go/baidu` | `https://www.baidu.com` | 演示外部跳转 |
| `github-route` | `/go/github` | `https://github.com` | 演示外部跳转 |
| `nacos-console` | `/go/nacos/**` | `http://127.0.0.1:8848` | 转发到 Nacos 控制台 |

### 3.3 断言演示路由（Predicate 示例）

| 路由 ID | 路径 | Predicate | 目标 |
|---------|------|-----------|------|
| `get-only-route` | `/demo/get-only` | `Method=GET` | httpbin.org/get |
| `header-route` | `/demo/with-header` | `Header=X-Token, .+` | httpbin.org/headers |
| `query-route` | `/demo/with-query` | `Query=color` | httpbin.org/get |
| `ip-route` | `/demo/ip-only` | `RemoteAddr=127.0.0.1, 192.168.0.0/16, 10.0.0.0/8` | httpbin.org/ip |
| `gray-a-route` | `/demo/gray` | `Weight=gray-group, 50` | httpbin.org（版本 A） |
| `gray-b-route` | `/demo/gray` | `Weight=gray-group, 50` | 百度（版本 B） |

### 3.4 路径重写路由

| 路由 ID | 路径 | 重写规则 | 用途 |
|---------|------|---------|------|
| `rewrite-route` | `/old-api/**` | `/old-api/xxx` → `/api/xxx` | 兼容旧路径 |

---

## 4. 全局过滤器（GlobalFilter）

### 4.1 TraceIdFilter

**功能**：为每个请求生成唯一链路追踪 ID

| 项目 | 说明 |
|------|------|
| 类 | `com.mymall.gateway.filter.TraceIdFilter` |
| 执行顺序 | `order = -200`（最先执行） |
| 请求头 | 检查 `X-Trace-Id`，不存在则生成 |
| 响应头 | 添加 `X-Trace-Id` 返回客户端 |
| 下游传递 | 通过 `X-Trace-Id` 头传递给后端服务 |

### 4.2 RequestLogFilter

**功能**：记录所有请求的日志和耗时

| 项目 | 说明 |
|------|------|
| 类 | `com.mymall.gateway.filter.RequestLogFilter` |
| 执行顺序 | `order = -100`（在 TraceIdFilter 之后） |
| 请求日志 | `>>> [Gateway] GET /api/product/list from 127.0.0.1` |
| 响应日志 | `<<< [Gateway] GET /api/product/list -> 200 (150ms) route=product-route` |

### 4.3 过滤器执行顺序

```
请求 → TraceIdFilter (-200) → RequestLogFilter (-100) → [框架 Filter] → 路由目标
响应 ← TraceIdFilter (-200) ← RequestLogFilter (-100) ← [框架 Filter] ← 路由目标
```

---

## 5. CORS 跨域配置

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "http://localhost:5173"  # Vite 默认端口
            allowed-methods: "*"
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600
```

---

## 6. Actuator 监控端点

| 端点 | 用途 | 示例 |
|------|------|------|
| `/actuator/gateway/routes` | 查看所有路由 | `GET http://localhost:1000/actuator/gateway/routes` |
| `/actuator/gateway/globalfilters` | 查看所有全局过滤器 | `GET http://localhost:1000/actuator/gateway/globalfilters` |
| `/actuator/gateway/routefilters` | 查看所有路由过滤器 | `GET http://localhost:1000/actuator/gateway/routefilters` |
| `/actuator/health` | 健康检查 | `GET http://localhost:1000/actuator/health` |
| `/actuator/metrics` | 性能指标 | `GET http://localhost:1000/actuator/metrics` |

**配置**：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,gateway,metrics
  endpoint:
    gateway:
      enabled: true
    health:
      show-details: always
```

---

## 7. HTTP 调试文件

项目提供 `http/gateway-demo.http` 文件，可在 IDEA 中直接运行。

### 7.1 负载均衡路由测试

```http
GET http://localhost:1000/api/product/list
GET http://localhost:1000/api/member/info
GET http://localhost:1000/api/coupon/list
```

### 7.2 外部 URL 路由测试

```http
GET http://localhost:1000/go/baidu
GET http://localhost:1000/go/github
GET http://localhost:1000/go/nacos
```

### 7.3 断言路由测试

```http
### GET 请求可访问
GET http://localhost:1000/demo/get-only

### 带 Header 可访问
GET http://localhost:1000/demo/with-header
X-Token: my-secret-token

### 带查询参数可访问
GET http://localhost:1000/demo/with-query?color=red

### 灰度路由（50% A / 50% B）
GET http://localhost:1000/demo/gray
```

### 7.4 链路追踪测试

```http
### 自动生成 TraceId
GET http://localhost:1000/demo/get-only

### 手动传入 TraceId
GET http://localhost:1000/demo/get-only
X-Trace-Id: my-custom-trace-id-12345
```

---

## 8. 生产环境配置建议

### 8.1 路由配置放 Nacos

生产环境建议将路由规则放到 Nacos 配置中心，实现动态更新：

**Nacos 配置 `mall-gateway.yaml`**：

```yaml
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

### 8.2 Sentinel 限流（待实现）

```yaml
spring:
  cloud:
    sentinel:
      transport:
        port: 8719
        dashboard: localhost:8080
```

网关层使用 `SentinelGatewayFilter` + `SentinelGatewayBlockExceptionHandler`。

### 8.3 JWT 鉴权（待实现）

实现 `AuthGlobalFilter`：
- order = -50（在 RequestLogFilter 之后，框架 Filter 之前）
- 白名单路径不需要鉴权
- 验证 JWT 后提取用户信息传递给下游

### 8.4 生产级部署

| 项目 | 建议 |
|------|------|
| 实例数 | ≥ 2 |
| 负载均衡 | Nginx / K8s Ingress |
| SSL | Nginx 终止 |
| 健康检查 | `/actuator/health` |
| 日志 | JSON 格式，收集到 ELK/Loki |
| 链路追踪 | 接入 OpenTelemetry + Jaeger/Tempo |

---

## 9. 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| `No route found` | 没有匹配的路由 | 检查 predicates 配置 |
| `404 Not Found` | StripPrefix 去错了 | 检查 StripPrefix 数值 |
| `Connection refused` | 下游服务未启动 | 确认目标服务已启动 |
| `502 Bad Gateway` | 下游服务连接失败 | 检查服务注册状态和端口 |
| `CORS error` | 跨域配置不匹配 | 检查 `allowed-origins` |
| `lb:// not work` | 未引入 LoadBalancer | 检查 pom.xml 依赖 |

---

## 10. 相关文档

| 文档 | 位置 |
|------|------|
| 网关学习文档 | `docs/learn-docs/api-gateway/` |
| 服务注册配置 | `docs/service-registration-config.md` |
| Nacos 配置中心 | `docs/nacos-config-guide.md` |
| 端口分配规划 | `docs/service-registration-config.md` § 端口分配表 |
| HTTP 调试文件 | `http/gateway-demo.http` |
