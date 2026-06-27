# mall-common 学习笔记

> 本目录讲解 `mall-common` 公共模块中各组件的**设计原理**——为什么这样设计、业界有哪些方案、各自取舍。
> API 和包结构等"是什么"的信息见开发文档 [common-module-design.md](../../common/common-module-design.md)。

## 组件索引

各主题独立，按需阅读，无先后顺序。

| 主题 | 涉及组件 | 一句话 | 详解 |
|------|---------|--------|------|
| 公共模块总览 | 自动装配机制 | 为什么需要公共模块、Spring Boot 自动装配原理 | [01-overview.md](./01-overview.md) |
| 统一响应与异常体系 | `R` `PageVO` `ResultCode` `BizException` `GlobalExceptionHandler` | 从 Controller 到 Service 的完整错误处理链路 | [02-response-exception.md](./02-response-exception.md) |
| 数据审计与用户上下文 | `BaseEntity` `MyMetaObjectHandler` `UserContext` | 字段自动填充如何与 ThreadLocal 上下文串联 | [03-audit-context.md](./03-audit-context.md) |
| 请求参数基础设施 | `PageQuery` `PageUtils` `Create` `Update` | 分页参数统一 + 分页转换工具 + 校验分组设计 | [04-query-validation.md](./04-query-validation.md) |
| 全局配置类 | `MybatisPlusConfig` `JacksonConfig` `SpringDocConfig` | 三个配置类各自解决什么问题 | [05-global-config.md](./05-global-config.md) |
| 对象存储 SDK | `OssTemplate` `OssProperties` `OssAutoConfiguration` | Spring Boot starter 设计模式实战 | [06-oss-sdk.md](./06-oss-sdk.md) |
