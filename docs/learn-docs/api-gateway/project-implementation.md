# 网关项目实施记录

> 记录本项目中 API 网关的实现细节与进度

## 1. 当前状态

| 项目 | 状态 |
|------|------|
| 依赖配置 (pom.xml) | ✅ 已完成 |
| 启动类 | ⏳ 待创建 |
| application.yml | ⏳ 待创建 |
| Nacos 注册 | ⏳ 待配置 |
| 路由配置 | ⏳ 待配置 |
| 鉴权 Filter | ⏳ 待实现 |
| Sentinel 限流 | ⏳ 待实现 |
| CORS 配置 | ⏳ 待实现 |

## 2. 依赖清单

`mall-gateway/pom.xml` 已配置的依赖：

| 依赖 | 用途 | 状态 |
|------|------|------|
| `spring-cloud-starter-gateway` | 网关核心（WebFlux + Netty） | ✅ |
| `spring-cloud-starter-alibaba-nacos-discovery` | 服务注册与发现 | ✅ |
| `spring-cloud-starter-alibaba-nacos-config` | 配置中心（动态路由） | ✅ |
| `spring-cloud-starter-loadbalancer` | 客户端负载均衡 | ✅ |
| `spring-cloud-starter-alibaba-sentinel` | 限流核心 | ✅ |
| `spring-cloud-alibaba-sentinel-gateway` | Sentinel 网关适配器 | ✅ |
| `spring-boot-starter-data-redis-reactive` | Redis 响应式（限流计数器） | 已注释 |
| `springdoc-openapi-starter-webflux-ui` | API 文档（响应式） | ✅ |
| `spring-boot-starter-actuator` | 健康检查与监控端点 | ✅ |
| `lombok` | 简化代码 | ✅ |

### 依赖注意事项

- **不能引入** `spring-boot-starter-web`，会与 Gateway（WebFlux）冲突
- Redis 依赖使用 `reactive` 版本，与响应式模型匹配
- Sentinel Gateway 适配器必须与 Sentinel 核心一起引入

## 3. 计划架构

### 3.1 服务信息

| 属性 | 值 |
|------|---|
| 服务名 | `mall-gateway` |
| 端口 | `1000` |
| Nacos Namespace | `my-mall` |
| 数据库 | 无（网关不直连数据库） |

### 3.2 路由规划

| 路径前缀 | 目标服务 | StripPrefix |
|---------|---------|-------------|
| `/api/product/**` | `lb://mall-product` | 1 |
| `/api/member/**` | `lb://mall-member` | 1 |
| `/api/order/**` | `lb://mall-order` | 1 |
| `/api/coupon/**` | `lb://mall-coupon` | 1 |
| `/api/ware/**` | `lb://mall-ware` | 1 |
| `/api/auth/**` | `lb://mall-auth` | 1 |
| `/api/search/**` | `lb://mall-search` | 1 |
| `/api/cart/**` | `lb://mall-cart` | 1 |
| `/api/seckill/**` | `lb://mall-seckill` | 1 |

### 3.3 过滤器规划

| Filter | 类型 | Order | 说明 |
|--------|------|-------|------|
| TraceFilter | GlobalFilter | -300 | 生成 TraceId |
| AuthGlobalFilter | GlobalFilter | -200 | JWT 鉴权 |
| RequestLogFilter | GlobalFilter | -100 | 请求日志 |
| Sentinel Gateway | GatewayFilter | — | 限流（框架内置） |

### 3.4 application.yml 规划

```yaml
server:
  port: 1000

spring:
  application:
    name: mall-gateway
  config:
    import:
      - "nacos:mall-gateway.yaml?refreshEnabled=true"
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
    gateway:
      discovery:
        locator:
          enabled: false       # 不使用自动路由，手动配置更精细
      routes:
        - id: product-route
          uri: lb://mall-product
          predicates:
            - Path=/api/product/**
          filters:
            - StripPrefix=1

management:
  endpoints:
    web:
      exposure:
        include: health,gateway
  endpoint:
    gateway:
      enabled: true
```

### 3.5 Nacos 配置 (mall-gateway.yaml)

路由和跨域配置放入 Nacos，支持动态修改：

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "http://localhost:5173"
            allowed-methods: "*"
            allowed-headers: "*"
            allow-credentials: true
      routes:
        - id: product-route
          uri: lb://mall-product
          predicates:
            - Path=/api/product/**
          filters:
            - StripPrefix=1
        - id: member-route
          uri: lb://mall-member
          predicates:
            - Path=/api/member/**
          filters:
            - StripPrefix=1
        - id: coupon-route
          uri: lb://mall-coupon
          predicates:
            - Path=/api/coupon/**
          filters:
            - StripPrefix=1
```

## 4. 实现步骤（计划）

1. **创建启动类** `GatewayApplication.java`
2. **配置 application.yml**（Nacos 注册 + 基础配置）
3. **配置路由**（YAML 或 Nacos 配置中心）
4. **实现 GlobalFilter**
   - TraceFilter（链路追踪）
   - AuthGlobalFilter（JWT 鉴权）
   - RequestLogFilter（请求日志）
5. **配置 CORS**（跨域）
6. **配置 Sentinel 限流**
7. **验证与调试**

## 5. 相关文件

| 文件 | 说明 |
|------|------|
| `mall-gateway/pom.xml` | 依赖配置（已完成） |
| `mall-gateway/src/main/java/.../GatewayApplication.java` | 启动类（待创建） |
| `mall-gateway/src/main/resources/application.yml` | 本地配置（待创建） |
| `docs/nacos-config-guide.md` | 配置中心使用指南 |
| `docs/service-registration-config.md` | 服务注册配置说明 |

## 6. 更新日志

| 日期 | 内容 |
|------|------|
| — | 初始化 pom.xml，配置 Gateway + Nacos + Sentinel 依赖 |
| — | 创建学习文档（网关基础/架构/路由过滤器/生产实践） |
