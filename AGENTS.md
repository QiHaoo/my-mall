# 商城系统 (my-mall)

> 学习项目，技术选型原则：最优 / 最流行 / 最值得学

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

## 项目文档

> **文档管理约定**：AGENTS.md 保持精简，只放技术选型、架构概览、文档索引等高频参考信息。详细内容放到 `docs/` 目录下。对于执行过程中可能需要查阅的文档，在此处维护索引，确保通过索引即可快速定位。

### docs 目录结构

> `docs/` 根目录放全局文档；每个服务/模块在 `docs/` 下建同名子目录，该模块所有文档（含 PROGRESS）放其中。

| 文档 | 说明 |
|------|------|
| `docs/PROGRESS.md` | **项目总进度**（已完成 + 当前进行，不列计划） |
| `docs/_TEMPLATE-PROGRESS.md` | 服务进度文档模板（复制到各服务目录命名为 PROGRESS.md） |
| `docs/{服务名}/` | 某服务的所有文档，含 `PROGRESS.md` 及该模块的设计、接口文档等 |
| `docs/git-workflow.md` | Git 管理规范（分支策略、Commit 规范、发布流程） |
| `docs/tech-stack-and-architecture-2026.md` | 技术选型与架构设计（选型理由、架构图、服务划分） |
| `docs/local-dev-reference.md` | 本地开发环境手册（服务连接信息、启停命令、IDEA 配置） |
| `docs/service-registration-config.md` | 服务注册配置说明（Nacos 配置、禁用项、依赖清理、端口规划） |
| `docs/mybatis-plus-codegen-guide.md` | MyBatis-Plus 代码生成规范（生成器使用、包结构、Entity/Mapper/Service/Controller 规范） |
| `docs/development-workflow.md` | AI 辅助开发流程规范（轻量 Spec + TDD，三阶段流程、核心/样板判定、配套 skill） |
| `docs/other/` | 归档文档（原始架构分析、参考资料等，开发时一般不需要） |

> **进度文档与提交关联规则**：完成一个事项后，将过程中的多个小提交合并为一条提交并推送到远程，再在进度文档记录关联的提交 hash。过程中未合并推送的小提交不记录。
>
> **⚠️ 提交前必做**：每次要做新提交之前，先更新进度文档（`docs/PROGRESS.md` 或 `docs/{服务名}/PROGRESS.md`），确认记录完整后再执行 commit。进度文档与代码提交必须同步，不允许先提交后补文档。
>
> **进度文档记录范围**：只记录对项目实现有重要影响的事项（新功能、架构调整、关键 bug 修复等）。依赖版本调整、配置文件微调、文档格式修正等次要变更不记入进度文档。

### 根目录配置文件

| 文件 | 说明 |
|------|------|
| `docker-compose.yml` | 全部中间件编排（profiles 分组：core/mq/search/storage/monitor） |
| `dev-environment-setup.md` | 开发环境搭建指南（WSL2+Docker，从零到可用） |
| `config/prometheus/prometheus.yml` | Prometheus 采集配置 |
| `init/` | 数据库初始化脚本 |
