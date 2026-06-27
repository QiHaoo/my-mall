# 远程调用（OpenFeign）

> 学习微服务间声明式 HTTP 调用的原理与实践，以 OpenFeign + LoadBalancer 为核心。

## 📚 文档目录

| 文档 | 内容 | 适合 |
|------|------|------|
| [01-远程调用基础](./01-feign-basics.md) | RPC vs HTTP、声明式调用的思想 | 入门 |
| [02-Feign 核心机制](./02-feign-core.md) | 动态代理、编解码、拦截器 | 进阶 |
| [03-负载均衡](./03-loadbalancer.md) | 客户端负载均衡原理、与 Feign 的集成 | 进阶 |
| [04-最佳实践](./04-best-practices.md) | 超时配置、重试、日志、熔断降级 | 实战 |
| [项目实施记录](./project-implementation.md) | 本项目中 Feign 的具体配置与调用链路 | 项目 |

## 🎯 学习路径

```
远程调用基础 ──► Feign 核心机制 ──► 负载均衡 ──► 最佳实践
                                                     │
                                                     ▼
                                               项目实施记录
```

## 📋 核心知识点

### 1. 远程调用基础
- RPC vs RESTful 对比
- 为什么需要声明式 HTTP 客户端
- 传统 RestTemplate 的痛点
- Feign 的核心理念：接口 = 远程服务

### 2. Feign 核心机制
- JDK 动态代理 + 注解解析
- 请求模板构建（URL、Header、Body）
- 编解码器（Encoder/Decoder）
- 拦截器链（RequestInterceptor）
- 与 Spring MVC 注解的复用

### 3. 负载均衡
- 服务端 vs 客户端负载均衡
- Ribbon 退役 → Spring Cloud LoadBalancer
- 与 Nacos 服务发现的协作
- 负载均衡策略（轮询、随机、权重）

### 4. 最佳实践
- 超时配置（connect / read）
- 重试策略（哪些场景适合 / 不适合重试）
- 日志级别（NONE / BASIC / HEADERS / FULL）
- 与 Sentinel 集成熔断降级
- 契约测试（Spring Cloud Contract）

## 🔗 相关资源

- [OpenFeign 官方文档](https://docs.spring.io/spring-cloud-openfeign/)
- [Spring Cloud LoadBalancer 文档](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer)
- [项目远程调用配置](../../microservice/feign-config.md)
