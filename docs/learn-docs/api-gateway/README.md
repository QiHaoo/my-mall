# API 网关

> 学习微服务架构中的 API 网关层设计，以 Spring Cloud Gateway 为核心

## 📚 文档目录

| 文档 | 内容 | 适合 |
|------|------|------|
| [01-网关基础](./01-api-gateway-basics.md) | 为什么需要网关、网关类型对比 | 入门 |
| [02-Spring Cloud Gateway 架构](./02-spring-cloud-gateway-architecture.md) | 核心架构、响应式编程模型 | 进阶 |
| [03-路由与过滤器](./03-route-predicate-filter.md) | 路由配置、Predicate、Filter | 实战 |
| [04-生产实践](./04-production-practices.md) | 鉴权、限流、监控、最佳实践 | 进阶 |
| [项目实施记录](./project-implementation.md) | 本项目的网关配置与实践 | 项目 |

## 🎯 学习路径

```
网关基础 ──► Spring Cloud Gateway 架构 ──► 路由与过滤器实战
                                                │
                                                ▼
                                          生产实践 → 项目实施记录
```

## 📋 核心知识点

### 1. 基础概念
- API 网关的作用与职责
- 主流网关方案对比（Spring Cloud Gateway / Zuul / Kong / APISIX）
- 反向代理 vs API 网关
- BFF（Backend For Frontend）模式

### 2. Spring Cloud Gateway
- 响应式编程模型（基于 WebFlux + Netty）
- Route / Predicate / Filter 三要素
- 动态路由与服务发现集成
- 全局过滤器 vs 路由过滤器

### 3. 实战技能
- YAML 路由配置
- 内置 Predicate 工厂
- 内置 Filter 工厂
- 自定义 GatewayFilter
- 自定义 GlobalFilter
- 跨域配置

### 4. 生产实践
- 统一鉴权（JWT / Token）
- 限流（Sentinel Gateway）
- 灰度发布（按权重/Header/IP）
- 链路追踪
- 高可用部署

## 🔗 相关资源

- [Spring Cloud Gateway 官方文档](https://docs.spring.io/spring-cloud-gateway/reference/)
- [Spring Cloud Alibaba Sentinel Gateway](https://spring-cloud-alibaba-group.github.io/github-pages/)
- [Project Reactor 文档](https://projectreactor.io/docs/core/release/reference/)
