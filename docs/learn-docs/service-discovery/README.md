# 服务注册与发现

> 学习微服务架构中的服务注册与发现机制，以 Nacos 为核心

## 📚 文档目录

| 文档 | 内容 | 适合 |
|------|------|------|
| [01-基础概念](./01-service-discovery-basics.md) | 服务注册与发现原理、注册中心对比 | 入门 |
| [02-Nacos 架构](./02-nacos-architecture.md) | Nacos 核心组件、数据模型、一致性协议 | 进阶 |
| [03-Spring Cloud Alibaba](./03-spring-cloud-alibaba-nacos.md) | 实战配置、服务调用、高级用法 | 实战 |
| [04-最佳实践](./04-best-practices.md) | 生产配置、问题排查、性能优化 | 进阶 |
| [项目实施记录](./project-implementation.md) | 本项目的具体配置与进度 | 项目 |

## 🎯 学习路径

```
基础概念 ──► Nacos 架构 ──► Spring Cloud Alibaba 实战 ──► 最佳实践
                                                       │
                                                       ▼
                                                 项目实施记录
```

## 📋 核心知识点

### 1. 基础概念
- 服务注册与发现的作用
- 主流注册中心对比（Nacos/Eureka/Consul/Zookeeper）
- 临时实例 vs 持久实例
- 命名空间与分组

### 2. Nacos 核心
- 架构设计（Naming Service / Config Service）
- 数据模型（Namespace → Group → Service → Instance）
- 一致性协议（Distro / Raft）
- 健康检查机制

### 3. 实战技能
- Spring Cloud Alibaba Nacos Discovery 配置
- 服务注册与发现
- OpenFeign 服务调用
- 元数据路由
- 多环境配置

### 4. 生产实践
- 高可用部署
- 优雅下线
- 问题排查
- 性能优化
- 监控告警

## 🔗 相关资源

- [Nacos 官方文档](https://nacos.io/docs/latest/overview/)
- [Spring Cloud Alibaba 文档](https://spring-cloud-alibaba-group.github.io/github-pages/)
- [Nacos GitHub](https://github.com/alibaba/nacos)
