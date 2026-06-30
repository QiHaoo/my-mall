# 商城系统 (my-mall)

> 学习项目，技术选型原则：最优 / 最流行 / 最值得学

## ⚠️ 核心原则：生产级标准

虽然是学习项目，但**所有代码、配置、规范、文档都必须严格按照生产环境标准执行**。

- 命名规范、配置规范、异常处理、日志规范等不允许简化或省略
- 不允许出现仅适用于学习/演示环境的写法（如硬编码密码、简化异常处理、省略日志等）
- 给出的规范、配置模板必须能直接用于生产环境，不能学到生产环境会出问题的做法
- 演示功能时可以用简化数据，但架构和工程实践不能简化

目的：确保学到的东西能够直接应用于生产环境，避免“学的”和“用的”脱节。

## 开发环境约定

- **JDK 21 路径**：`D:\DeveloperUtil\JDK21`（OpenJDK 21.0.11 LTS）。项目 `java.version=21`，系统默认 `java` 可能指向 JDK 17，编译/运行前需显式指定：
  ```bash
  export JAVA_HOME="/d/DeveloperUtil/JDK21"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
  或在 Maven 命令前加 `JAVA_HOME`：`JAVA_HOME=/d/DeveloperUtil/JDK21 mvn test`。

## 技术选型

| 类别 | 选型 |
|------|------|
| 开发语言 | Java 21 (LTS) |
| 核心框架 | Spring Boot 3.4 |
| 微服务套件 | Spring Cloud 2024.0 + Spring Cloud Alibaba 2023.0 |
| 注册/配置中心 | Nacos 2.4 |
| API 网关 | Spring Cloud Gateway 4.x |
| 负载均衡 | Spring Cloud LoadBalancer |
| 服务调用 | OpenFeign + Spring 6 HTTP Interface |
| 熔断降级 | Resilience4j（代码级）+ Sentinel（规则级） |
| 认证授权 | Spring Authorization Server + Spring Security 6 |
| 分布式事务 | Seata 2.1 |
| 数据库 | MySQL 8.4 (LTS) + ShardingSphere 5.5（分库分表） |
| 缓存 | Redis 7.4 Cluster + Redisson（分布式锁） |
| 消息队列 | RocketMQ 5.3 |
| 搜索引擎 | OpenSearch 2.x |
| 对象存储 | MinIO |
| 任务调度 | XXL-Job 2.4 |
| 数据同步 | Canal 1.1（MySQL binlog → MQ → ES/缓存） |
| 链路追踪 | Micrometer Tracing + OpenTelemetry + Tempo |
| 监控 | Prometheus + Grafana + VictoriaMetrics |
| 日志 | Loki + Promtail |
| 告警 | Alertmanager + Grafana Alerting |
| 容器化 | Docker + K8s 1.30 + Helm |
| CI/CD | GitHub Actions (CI) + ArgoCD (GitOps CD) |
| 镜像仓库 | Harbor 2.x |
| ORM | MyBatis-Plus 3.5+ |
| API 文档 | SpringDoc OpenAPI 3 |
| 前端 | Vue 3 + TypeScript + Vite |
| UI 组件库 | Element Plus |
| 前端设计 | Figma（免费版） |

## 架构

```
用户终端 → CDN/WAF → Spring Cloud Gateway（鉴权/限流/路由）
  → LoadBalancer
    → 业务微服务（OpenFeign + Resilience4j + Sentinel + Seata）
      → MySQL / Redis / RocketMQ / OpenSearch / MinIO

可观测性：OpenTelemetry → Tempo(追踪) + Prometheus(指标) + Loki(日志) → Grafana
CI/CD：  GitHub Actions → Harbor → ArgoCD → K8s
```

**服务治理机制**

| 能力 | 实现 |
|------|------|
| 服务注册与发现 | Nacos |
| 客户端负载均衡 | Spring Cloud LoadBalancer |
| 服务间调用 | OpenFeign / Spring 6 HTTP Interface |
| 熔断降级 | Resilience4j（方法级）+ Sentinel（接口级 QPS/热点限流） |
| 配置管理 | Nacos（动态推送、环境隔离、版本回滚） |
| 分布式事务 | Seata AT（自动补偿）/ TCC（手动确认） |
| 认证授权 | Spring Authorization Server 颁发 JWT + Spring Security 6 校验 |

## 服务划分

| 服务 | 职责 | 数据库 |
|------|------|--------|
| `mall-gateway` | API 网关（统一入口、鉴权、限流、路由） | — |
| `mall-auth` | 认证授权（OAuth2 登录、JWT 颁发） | `mall_auth` |
| `mall-member` | 会员中心（注册、登录、收货地址、积分） | `mall_member` |
| `mall-product` | 商品中心（SPU/SKU、分类、品牌、属性） | `mall_product` |
| `mall-search` | 搜索服务（商品检索、聚合筛选） | — |
| `mall-cart` | 购物车（Redis Hash 存储） | — |
| `mall-order` | 订单中心（下单、状态流转、超时取消） | `mall_order` |
| `mall-ware` | 库存中心（库存扣减、回滚、预警） | `mall_ware` |
| `mall-coupon` | 营销中心（优惠券、满减、促销活动） | `mall_coupon` |
| `mall-seckill` | 秒杀服务（Redis 预减库存 + RocketMQ 削峰） | `mall_seckill` |
| `mall-third` | 第三方服务（短信、支付、OSS 上传） | — |
| `mall-admin` | 后台管理（商品上下架、订单管理、数据看板） | — |
| `mall-oss` | 对象存储（MinIO Presigned URL 签发、文件元数据管理） | `mall_oss` |

## 代码组织结构

> 每个业务模块统一遵循以下包结构，`{module}` 替换为模块名。

```
com.mymall.{module}/
├── {Module}Application.java      # 启动类（保持精简，不放 @MapperScan）
├── controller/                    # REST Controller
├── feign/                         # Feign 远程调用客户端接口（@FeignClient）
├── entity/                        # 数据库实体
├── mapper/                        # MyBatis Mapper（单表 CRUD 用 BaseMapper）
├── service/                       # Service 接口
│   └── impl/                      # Service 实现
└── config/                        # 本模块独有配置（MyBatisConfig / FeignConfig 等）
```

> **@MapperScan 必须独立**：不要将 `@MapperScan` 写在 `{Module}Application` 启动类上，必须单独放到 `config/MyBatisConfig.java` 中。否则 `@WebMvcTest` 切片测试时会尝试初始化 Mapper Bean，因缺少 DataSource 导致 ApplicationContext 加载失败。
```

> **mall-common 特殊结构**

```
com.mymall.common/
├── config/                        # 全局配置（MybatisPlusConfig 等）
├── entity/                        # 公共实体基类（BaseEntity）
├── handler/                       # 处理器（MyMetaObjectHandler 等）
├── oss/                           # OSS 自动配置（OssProperties + OssTemplate + OssAutoConfiguration）
├── util/                          # 工具类
└── exception/                     # 全局异常定义
```

> **common 模块补充原则**：开发过程中发现某些通用能力（工具类、工具方法、公共配置等）应由 common 提供但缺失时，**必须自行在 mall-common 中补充**，而非在各业务模块中重复实现。补充后在 `docs/common/common-module-design.md` 的速查表和组件职责章节同步登记。

## 项目文档

> **文档管理约定**：AGENTS.md 保持精简，只放技术选型、架构概览、文档索引等高频参考信息。详细内容放到 `docs/` 目录下。对于执行过程中可能需要查阅的文档，在此处维护索引，确保通过索引即可快速定位。
>
> **注意**：`docs/learn-docs/` 目录存放个人学习笔记，不属于项目开发文档，不在本索引范围内。学习笔记的组织原则见 [docs/learn-docs/README.md](docs/learn-docs/README.md)。

### docs 目录结构

> `docs/` 根目录放全局文档；每个服务/模块在 `docs/` 下建同名子目录，该模块所有文档（含 PROGRESS）放其中。

| 文档 | 说明 |
|------|------|
| `docs/PROGRESS.md` | **项目总进度**（已完成 + 当前进行，不列计划） |
| `docs/_TEMPLATE-PROGRESS.md` | 服务进度文档模板（复制到各服务目录命名为 PROGRESS.md） |
| `docs/{服务名}/` | 某服务的所有文档，含 `PROGRESS.md` 及该模块的设计、接口文档等 |
| `docs/doc-convention.md` | 文档写作规范（模块文档组织、图表规范 Mermaid vs 纯文本） |
| `docs/documentation-system-review.md` | 文档体系与开发体系审视报告（已有强项、缺失项、补充优先级与落地计划） |
| `docs/learn-docs/README.md` | learn-docs 学习笔记组织原则 |
| `docs/mall-product/overview.md` | 商品中心模块概述（职责、功能域划分、核心概念 SPU/SKU/属性、整体 ER 图、功能文档索引） |
| `docs/mall-product/category-management.md` | 商品分类管理需求与接口文档（三级分类树、批量删除、拖拽排序、DTO/错误码） |
| `docs/mall-product/brand-management.md` | 品牌管理需求与接口文档（品牌 CRUD、品牌-分类多对多关联、冗余字段同步） |
| `docs/mall-product/attrgroup-management.md` | 属性分组管理需求与接口文档（属性分组 CRUD、分组-属性关联管理、1:1 业务约束） |
| `docs/mall-product/attr-management.md` | 属性管理需求与接口文档（规格参数/销售属性 CRUD、共用接口按 attrType 区分） |
| `docs/mall-product/spu-management.md` | SPU 管理需求与接口文档（SPU CRUD、上下架、多表事务写入） |
| `docs/mall-product/sku-management.md` | SKU 管理需求与接口文档（SKU CRUD、销售属性组合唯一性校验） |
| `docs/mall-product/object-storage-design.md` | 对象存储服务设计文档（MinIO 自建、Presigned URL 前端直传、mall-oss 服务 + mall-common-oss SDK） |
| `docs/common/common-module-design.md` | mall-common 公共模块设计（包结构、自动装配、各组件职责、组件速查表、演进记录） |
| `docs/common/doc-convention.md` | common 模块文档组织规范（单文档+速查表模式、复杂组件独立文档判定、新增组件流程） |
| `docs/frontend/overview.md` | 前端架构概述（项目结构、技术栈、路由设计、状态管理、API 层封装、后端协作约定） |
| `docs/frontend/infrastructure.md` | 前端基础设施设计（Vite/TS/ESLint 配置、样式体系、布局组件、踩坑记录、待实现组件规划） |
| `docs/frontend/components.md` | 前端通用组件与 composables 设计（PageTable/FormDialog/useTable/useDialog/request/tree 速查表） |
| `docs/frontend/coding-standards.md` | 前端编码规范（SFC 结构、Composition API、TypeScript、命名、样式、代码风格） |
| `docs/frontend/design-system.md` | 前端设计系统（Element Plus 定制、主题色、通用组件视觉与交互规范、Figma 设计稿交付流程） |
| `docs/frontend/PROGRESS.md` | 前端开发进度 |
| `docs/frontend/figma-mcp-guide.md` | AI Agent 操作 Figma 设计稿指南（figma-console-mcp 选型、配置、连接验证、生成流程、核心工具速查） |
| `docs/standards/git-workflow.md` | Git 管理规范（分支策略、Commit 规范、发布流程） |
| `docs/tech-stack-and-architecture-2026.md` | 技术选型与架构设计（选型理由、架构图、服务划分） |
| `docs/local-dev-reference.md` | 本地开发环境手册（服务连接信息、启停命令、IDEA 配置） |
| `docs/microservice/service-registration-config.md` | 服务注册配置说明（Nacos 配置、禁用项、依赖清理、端口规划） |
| `docs/microservice/nacos-config-guide.md` | Nacos 配置中心使用指南（依赖配置、动态刷新、演示接口、多环境配置） |
| `docs/standards/mybatis-plus-codegen-guide.md` | MyBatis-Plus 代码生成规范（生成器使用、包结构、Entity/Mapper/Service/Controller 规范） |
| `docs/standards/ai-assisted-development.md` | AI 辅助开发体系（Skill 体系、上下文管理、AGENTS.md 解读） |
| `docs/standards/development-workflow.md` | AI 辅助开发流程规范（轻量 Spec + TDD，三阶段流程、核心/样板判定、配套 skill） |
| `docs/standards/controller-specification.md` | Controller 接口编写规范（参数校验、校验分组、返回值、URL 设计、分页、幂等性、检查清单） |
| `docs/standards/security-specification.md` | 安全与认证授权规范（OAuth2 流程、JWT 设计、RBAC 鉴权、敏感数据保护、密钥管理、攻击防护） |
| `docs/standards/coding-standards.md` | 编码规范（分层架构、Service 事务、异常体系、GlobalExceptionHandler、日志、DTO/VO/Entity、命名规范） |
| `docs/standards/testing-specification.md` | 测试规范（测试分层、各层写法、AssertJ、E2E、公共组件/网关测试、覆盖率门禁 Jacoco、CI 集成、前端测试体系规划、提交前自查清单、待补充事项行动清单） |
| `docs/standards/logging-specification.md` | 日志输出规范（整体架构、MDC 链路追踪、logback-spring.xml 配置模板、各环境差异、文件滚动归档、MyBatis SQL 日志、Loki 采集、敏感信息脱敏） |
| `docs/standards/ci-cd/overview.md` | CI/CD 总体设计（方案选型、CI/CD 边界、编排器结构、关键决策、分阶段实施路径） |
| `docs/standards/ci-cd/backend-ci.md` | 后端 CI 工作流设计（mvn verify、jacoco 门禁、composite action、前置条件） |
| `docs/standards/ci-cd/frontend-ci.md` | 前端 CI 工作流设计（pnpm lint/type-check/build、vitest 留位、scripts 调整） |
| `docs/standards/ci-cd/docker-publish.md` | 镜像构建与 Harbor 集成（多阶段 Dockerfile、矩阵并行、tag 策略、Trivy 扫描） |
| `docs/standards/ci-cd/harbor-setup.md` | Harbor 部署指南（独立部署、项目/机器人账号配置、镜像保留策略、生产环境注意事项） |
| `docs/standards/ci-cd/release-and-protection.md` | Release 发布与分支保护（Changelog 自动生成、prerelease 判断、Secrets/Variables、main/develop 保护规则） |
| `docs/learn-docs/ci/README.md` | 持续集成学习笔记（Docker 基础→CI 概念→测试→镜像→发布→项目方案解读，6 篇） |
| `docs/microservice/feign-config.md` | 远程调用（Feign）配置说明（依赖、@FeignClient 规范、调用链路、测试配置） |
| `docs/microservice/gateway-config-guide.md` | API 网关配置指南（路由规则、全局过滤器、断言演示、生产配置） |
| `docs/standards/mybatis-plus-orm-notes.md` | MyBatis-Plus ORM 实践笔记（依赖配置、全局组件、使用约定） |
| `docs/other/` | 归档文档（原始架构分析、参考资料等，开发时一般不需要） |

> **进度文档与提交关联规则**：完成一个事项后，将过程中的多个小提交合并为一条提交并推送到远程，再在进度文档记录关联的提交 hash。过程中未合并推送的小提交不记录。
>
> **⚠️ 提交前必做**：每次要做新提交之前，先更新进度文档（`docs/PROGRESS.md` 或 `docs/{服务名}/PROGRESS.md`），确认记录完整后再执行 commit。进度文档与代码提交必须同步，不允许先提交后补文档。
>
> **⚠️ 提交必须经用户确认**：任何时候都不能在用户未明确确认的情况下执行 `git commit` 或 `git push`。做完代码修改后，先展示变更摘要并询问用户确认，用户说「可以提交」或明确同意后，再执行 commit / push。
>
> **进度文档记录范围**：只记录对项目实现有重要影响的事项（新功能、架构调整、关键 bug 修复等）。依赖版本调整、配置文件微调、文档格式修正等次要变更不记入进度文档。
>
> **代码注释规范**：项目中的代码和配置文件要有适当的注释。难理解的逻辑、需要注意的坑、有学习价值的设计，注释要丰富；简单的 CRUD / 样板代码可以不写注释。

> **文档写作规范**：模块文档组织（按需演进 + 分层结构）、图表规范（Mermaid vs 纯文本）详见 [docs/doc-convention.md](docs/doc-convention.md)。common 模块的文档组织规范见 [docs/common/doc-convention.md](docs/common/doc-convention.md)。

### 根目录配置文件

| 文件 | 说明 |
|------|------|
| `docker-compose.yml` | 全部中间件编排（profiles 分组：core/mq/search/storage/monitor） |
| `dev-environment-setup.md` | 开发环境搭建指南（WSL2+Docker，从零到可用） |
| `config/prometheus/prometheus.yml` | Prometheus 采集配置 |
| `init/` | 数据库初始化脚本 |
