# Nacos 配置中心

> 学习微服务架构中的分布式配置管理，以 Nacos Config 为核心

## 📚 文档目录

| 文档 | 内容 | 适合 |
|------|------|------|
| [01-配置中心基础](./01-config-center-basics.md) | 为什么需要配置中心、核心概念 | 入门 |
| [02-Nacos 配置架构](./02-nacos-config-architecture.md) | Nacos Config 架构、数据模型 | 进阶 |
| [03-Spring Cloud 集成](./03-spring-cloud-nacos-config.md) | 实战配置、动态刷新、最佳实践 | 实战 |
| [项目实施记录](./project-implementation.md) | 本项目的配置中心实践 | 项目 |

## 🎯 学习路径

```
配置中心基础 ──► Nacos 配置架构 ──► Spring Cloud 集成实战
                                        │
                                        ▼
                                  项目实施记录
```

## 📋 核心知识点

### 1. 为什么需要配置中心？

- **配置分散**：微服务数量多，配置文件散落各处
- **配置动态性**：运行时需要调整配置（如开关、阈值）
- **环境隔离**：dev/test/prod 配置不同
- **版本管理**：配置变更需要可追溯、可回滚

### 2. 核心能力

| 能力 | 说明 |
|------|------|
| **集中管理** | 所有配置统一存储 |
| **动态刷新** | 配置变更实时推送 |
| **环境隔离** | Namespace 隔离不同环境 |
| **版本控制** | 配置历史、回滚能力 |
| **灰度发布** | 按 IP/标签灰度推送 |

### 3. Spring Cloud 集成要点

```yaml
# 关键配置
spring:
  config:
    import: "nacos:app.yaml?refreshEnabled=true"
```

```java
// 动态刷新
@RefreshScope
@Component
public class MyConfig {
    @Value("${my.config}")
    private String config;
}
```

## 🔗 相关资源

- [Nacos 官方文档 - 配置管理](https://nacos.io/docs/latest/guide/user/config/)
- [Spring Cloud Alibaba Nacos Config](https://spring-cloud-alibaba-group.github.io/github-pages/hoxton/zh-cn/index.html#_nacos_config)
