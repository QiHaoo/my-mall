# 文档体系与开发体系审视报告

> 本文档基于 2026-06-29 对 `docs/` 全目录、规范文档、各服务目录和 PROGRESS 的完整审视，梳理已有强项、缺失项与补充计划。
>
> **审视原则**：基于实际文件内容验证，不臆测。每个维度的结论均可追溯到具体文档。

---

## 一、已有的强项（无需补充，继续演进）

以下部分深度已达生产级标准，是文档体系的核心资产：

| 维度 | 关键文档 | 评价 |
|------|---------|------|
| 文档分层与 AI 辅助开发 | [documentation-layering-guide.md](standards/ai-assisted-development.md) + [development-workflow.md](standards/development-workflow.md) | 三件套闭环完整：分层规范 → AI 上下文管理 → 开发流程（Spec + TDD） |
| 编码规范 | [coding-standards.md](standards/coding-standards.md) | 分层架构、Service 事务、异常体系、GlobalExceptionHandler、日志、DTO/VO/Entity、命名规范全覆盖 |
| Controller 规范 | [controller-specification.md](standards/controller-specification.md) | URL 设计、参数校验、校验分组、返回值、分页、幂等性、检查清单 |
| 测试规范 | [testing-specification.md](standards/testing-specification.md) | 四层测试分层、AssertJ、E2E、覆盖率门禁 Jacoco、CI 集成、前端测试规划、26 项行动清单 |
| 表设计规范 | [table-design-specification.md](standards/table-design-specification.md) | 建表模板、审计字段、类型/索引/命名规范、ORM 对应表、检查清单 |
| CI 设计 | [ci-cd/](standards/ci-cd/) 6 篇 | CI 全链路设计完整（编排器 + 后端 CI + 前端 CI + 镜像发布 + Harbor + Release + 分支保护） |
| 商品中心模块文档 | [mall-product/overview.md](mall-product/overview.md) + 7 篇功能域文档 | 模块概述 + ER 图 + 功能文档，是其他模块的范本 |
| 前端规范 | [frontend/](frontend/) 7 篇 | 架构概述、基础设施、组件、编码规范、设计系统、Figma MCP 指南 |
| 学习文档 | [learn-docs/](learn-docs/) | 体系丰富，与项目文档分离 |

---

## 二、三大严重缺口（与"生产级标准"原则直接冲突）

AGENTS.md 第一条原则："所有代码、配置、规范、文档都必须严格按照生产环境标准执行"。以下三块**完全空白或严重不足**，与该原则直接冲突。

### 缺口 1：安全规范（完全缺失）

技术选型表列了 `Spring Authorization Server + Spring Security 6`，但全项目**无任何安全设计文档**。

**已有内容（零散提及，不成体系）**：
- [gateway-config-guide.md](microservice/gateway-config-guide.md) §8.3 一句"JWT 鉴权（待实现）"
- [coding-standards.md](standards/coding-standards.md) §5.3 一句"不记录敏感信息"
- [mall-product/overview.md](mall-product/overview.md) §六 一句"管理接口需管理员权限（网关 JWT 鉴权，待实现）"

**完全缺失**：
- 认证授权整体设计（OAuth2 授权码流程、Token 颁发/刷新/注销）
- JWT 设计规范（Claims 结构、过期策略、签名算法、密钥轮换）
- 接口鉴权规范（白名单路径、RBAC 权限模型、用户上下文透传）
- 敏感数据加密规范（密码哈希、手机号/身份证存储加密、传输加密）
- SQL 注入防护规范（MyBatis `${}` vs `#{}` 使用约束）
- 密钥管理规范（密钥存储、轮换、泄露应急）
- 接口限流安全策略（Sentinel 规则设计文档缺失）

**补充文档**：[security-specification.md](standards/security-specification.md)（第一波已落地）

### 缺口 2：可观测性规范（完全缺失）

技术栈选型完整（Tempo/Prometheus/Loki/Alertmanager），`docker-compose.yml` 已起监控栈，但**无任何可观测性设计文档**。

**已有内容**：
- [coding-standards.md](standards/coding-standards.md) §5 基础日志规范（框架、级别、规则）
- [gateway-config-guide.md](microservice/gateway-config-guide.md) §4 TraceIdFilter + RequestLogFilter
- [local-dev-reference.md](local-dev-reference.md) 列出 Prometheus/Grafana 连接信息

**完全缺失**：
- 结构化日志规范（JSON 输出、MDC 字段约定、traceId/spanId 注入日志、Logback JSON 编码器配置）
- 敏感字段脱敏规范（只有一句"不记录"，无脱敏注解/工具类设计）
- 监控指标规范（业务指标定义、Micrometer 埋点约定、指标命名、Grafana Dashboard 设计）
- 链路追踪规范（OpenTelemetry SDK 配置、采样率、TraceContext 跨服务透传）
- 告警规范（告警分级、路由、通知渠道、抑制/收敛）

**补充时机**：第二波，服务跑起来需要监控时补。

### 缺口 3：跨服务协作规范（严重不足）

微服务架构的核心能力，但只有 [feign-config.md](microservice/feign-config.md) 覆盖 Feign 基础用法。Seata、Resilience4j、Sentinel、RocketMQ、Redisson **全部无设计文档**。

**已有内容**：
- [feign-config.md](microservice/feign-config.md)：Feign 远程调用配置（依赖、@FeignClient 规范、调用链路、测试配置）
- [controller-specification.md](standards/controller-specification.md) §8 幂等性（场景表 + clientToken + Redis SETNX）
- [service-registration-config.md](microservice/service-registration-config.md)：提及 Seata 排除、Resilience4j 依赖冲突修复

**完全缺失**：
- 分布式事务规范（Seata AT/TCC 选型、事务分组、全局事务 ID 透传、补偿机制）
- 熔断降级设计（Resilience4j 代码级 vs Sentinel 规则级的分工边界、降级策略、参数配置）
- 消息总线规范（RocketMQ Topic/Tag 命名、消息结构、消费组管理、可靠投递、顺序/延迟/事务消息）
- 分布式锁规范（Redisson 使用场景、锁粒度、锁超时、死锁预防）
- 幂等性系统性规范（接口层已有，缺消息消费幂等、分布式事务幂等）
- 服务间数据一致性规范（缓存与数据库一致性、Canal binlog 同步规范）

**补充时机**：第二波，开发 mall-order（Seata）、mall-seckill（Redis 锁 + MQ 削峰）、mall-cart（Redis）时同步写。

---

## 三、开发流程层面的缺口

| 缺口 | 说明 | 影响 | 补充时机 |
|------|------|------|---------|
| 数据库变更管理 | `init/` 只有初始化 SQL，无 Flyway/Liquibase 版本化变更 | 表结构变更不可追溯、不可回滚 | P2，表结构频繁变更时引入 |
| API 契约对外发布 | SpringDoc 已配置，但 API 文档访问路径、维护责任、对外发布规范未成文 | 前后端联调靠 .http 文件和口头沟通 | P3 |
| 需求文档与设计文档边界 | `.trae/documents/` 下有 PRD，未纳入 `docs/` 体系。需求/设计/实现三层边界模糊 | AI 上下文加载时不知道该看哪个 | P3 |
| 技术债务登记 | testing-specification.md §14 列了 26 项待补充，但分散在各文档，无集中登记 | 债务容易被遗忘 | P3 |
| 依赖升级/漏洞响应 | 父 POM 管理版本，但无第三方依赖升级策略、CVE 漏洞响应流程 | 依赖老化无机制推动 | P3 |
| 代码审查机制 | coding-standards.md §8 有检查清单，但 `requesting-code-review` 是否每次触发无保障 | 规范可能流于形式 | P3 |

---

## 四、模块覆盖层面的缺口

| 缺口 | 说明 | 补充时机 |
|------|------|---------|
| 其余 11 个服务无 overview.md | 只有 [mall-product/overview.md](mall-product/overview.md) 有完整概述 + ER 图。mall-order/mall-ware 等已有代码骨架但无数据模型文档 | P1，开发到对应服务时补 |
| 无全局 ER 图 | 跨服务关联关系（order↔ware↔product↔member）无图示 | P2，多服务联调时补 |
| 无全局数据字典 | 枚举值、状态码分散在各功能域文档，无集中索引 | P2 |
| 无分库分表规范 | ShardingSphere 5.5 在选型表里，但无分片键选择、分片策略文档 | P3，数据量达标时补 |

---

## 五、前端规范的次要缺口

[frontend/](frontend/) 7 篇已覆盖主体，以下为次要缺口：

| 缺口 | 说明 | 补充时机 |
|------|------|---------|
| 路由权限设计 | [overview.md](frontend/overview.md) §3.2 标注 beforeEach 鉴权 ❌、动态路由 ❌，无独立设计文档 | P1，mall-auth 落地后补 |
| 国际化（i18n）方案 | 完全未规划 | P3 |
| 主题切换规范 | [infrastructure.md](frontend/infrastructure.md) §2.5 标注暗黑模式 ❌，无设计文档 | P3 |
| 前端错误边界与异常上报 | 无 ErrorBoundary、无 Sentry 集成规划 | P3 |
| 前端测试规范落地 | [testing-specification.md](standards/testing-specification.md) §11 有规划但标注"待建立" | P2 |

---

## 六、文档自身的一致性问题

| 问题 | 说明 | 处理方式 |
|------|------|---------|
| AGENTS.md 索引路径过时 | `docs/git-workflow.md` 实际在 `docs/standards/git-workflow.md` | 第一波已修复 |
| CLAUDE.md 与 AGENTS.md 重复 | CLAUDE.md 是旧版规则文件，索引路径大量过时，与 AGENTS.md 高度重复。仅多一个"开发环境约定"（JDK 21 路径） | 第一波已将 JDK 路径合并到 AGENTS.md，CLAUDE.md 可删除 |
| CI 设计文档完整但 yaml 未落地 | [ci-cd/](standards/ci-cd/) 6 篇设计文档写好了完整 YAML，但 `.github/workflows/` 只有 mkdocs.yml | 第一波已落地 |

---

## 七、补充优先级与落地计划

### P0 — 第一波（已完成）

| 事项 | 交付物 | 状态 |
|------|--------|------|
| 审视报告 | `docs/documentation-system-review.md`（本文档） | ✅ |
| 安全规范 | `docs/standards/security-specification.md` | ✅ |
| CI workflow 落地 | `.github/workflows/ci.yml` + `backend-ci.yml` + `frontend-ci.yml` | ✅ |
| composite action 落地 | `.github/actions/setup-java/` + `setup-frontend/` | ✅ |
| PR/Issue 模板 | `.github/PULL_REQUEST_TEMPLATE.md` + `.github/ISSUE_TEMPLATE/` | ✅ |
| AGENTS.md 索引修复 | git-workflow 路径 + 新增安全规范索引 + JDK 路径约定 | ✅ |

### P1 — 第二波（开发到对应模块时补）

| 事项 | 触发时机 |
|------|---------|
| 可观测性规范（结构化日志 + 脱敏 + traceId 透传 + 监控指标 + 告警） | 服务跑起来需要监控时 |
| 跨服务协作规范（Seata + 熔断降级 + 消息总线 + 分布式锁 + 幂等系统性） | 开发 mall-order / mall-seckill / mall-cart 时 |
| 各服务 overview.md（mall-order / mall-ware / mall-coupon / mall-member） | 开发到对应服务时 |
| 前端路由权限设计 | mall-auth 落地后 |

### P2 — 第三波（接近生产时补）

| 事项 | 触发时机 |
|------|---------|
| K8s/Helm 部署文档、多环境管理、灰度/回滚 | CD 阶段启动时 |
| 数据库迁移规范（Flyway） | 表结构频繁变更时 |
| 全局 ER 图、全局数据字典 | 多服务联调时 |
| 前端测试规范落地 | 前端核心逻辑稳定后 |

### P3 — 时机成熟时补

| 事项 | 说明 |
|------|------|
| API 版本管理、游标分页 | 锦上添花，不影响核心功能 |
| i18n、主题切换、前端错误边界 | 生产级项目通常需要，但非阻塞 |
| 性能测试、契约测试、安全测试 | 测试体系的进阶补充 |
| 分库分表规范 | 数据量达标时 |
| 技术债务集中登记、依赖升级策略 | 工程治理进阶 |

---

## 八、总结

文档/规范的**骨架已完整**（分层、AI 辅助、编码、测试、CI 设计都到位），但**血肉在三个方向偏薄**：安全、可观测性、跨服务协作——这三块恰好是"生产级"与"demo 级"的分水岭。

第一波已补齐安全规范、落地 CI workflow 和 PR/Issue 模板，并修复文档索引一致性。第二波将在开发对应模块时补齐可观测性和跨服务协作规范。
