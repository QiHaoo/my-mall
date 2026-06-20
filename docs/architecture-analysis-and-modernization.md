# 谷粒商城微服务架构分析与 2026 现代化方案

> **项目定位：学习项目** — 技术选型以「最优 / 最流行 / 最值得学」为原则，不考虑迁移成本，直击行业一线。

---

## 一、现有架构分析（2020 年前）

### 1.1 架构总览

原架构是典型的 **Spring Cloud Alibaba** 微服务体系，核心组件如下：

| 层级 | 组件 | 说明 |
|------|------|------|
| **流量入口** | CDN + WAF + Nginx 集群 | 外网流量经过安全过滤和负载均衡 |
| **网关层** | Spring Cloud Gateway (Webflux) | 动态路由、认证授权、令牌限流 |
| **负载均衡** | Ribbon | 客户端负载均衡（已进入维护模式） |
| **熔断降级** | Sentinel | 流控、熔断、系统保护 |
| **注册/配置中心** | Nacos | 服务注册发现 + 配置管理 |
| **服务间调用** | OpenFeign | 声明式 HTTP 客户端 |
| **安全** | OAuth2.0 + Spring Security | 认证授权 |
| **缓存** | Redis (Sentinel + Shard) | 分布式缓存 |
| **持久化** | MySQL 主从 | 关系型数据库 |
| **消息队列** | RabbitMQ (Mirror) | 异步解耦 |
| **搜索** | Elasticsearch (Shard) | 全文检索 |
| **对象存储** | OSS | 文件/图片存储 |
| **日志** | ELK (Elasticsearch + Logstash + Kibana) | 日志采集与分析 |
| **链路追踪** | Sleuth + Zipkin | 分布式追踪 |
| **监控告警** | Prometheus + Grafana + Alertmanager | 指标采集与告警 |
| **CI/CD** | Jenkins + GitHub + Docker Registry + K8s | 持续集成与部署 |

### 1.2 现有架构的问题

1. **Ribbon 已停止维护** — Netflix Ribbon 于 2018 年进入维护模式，Spring Cloud 2020+ 已默认替换为 Spring Cloud LoadBalancer
2. **Sleuth 已被 Micrometer Tracing 取代** — Spring Cloud Sleuth 在 Spring Boot 3.x 中被 Micrometer Tracing 替代
3. **ELK 栈偏重** — Logstash 资源消耗大，中小规模场景下可用更轻量方案
4. **RabbitMQ Mirror 模式局限** — Mirror 模式在大规模下性能不佳，缺乏跨数据中心能力
5. **单一网关** — Spring Cloud Gateway 虽好，但缺少 API 治理、灰度发布等高级能力
6. **缺乏 Service Mesh** — 服务间通信逻辑嵌入业务代码（SDK 模式），耦合度高
7. **缺乏 GitOps / IaC** — Jenkins Pipeline 模式在 2026 年已不是最优选择
8. **缺少多云/混合云考虑** — 强绑定单一云厂商 OSS 等服务

---

## 二、2026 现代化架构方案

### 方案 A：Spring Cloud 2024+ 全栈升级（推荐 — 平滑迁移）

> 适合：已有 Spring Cloud 微服务团队，希望平滑升级

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         用户终端 (Web / App / 小程序)                     │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │  CloudFlare / 阿里云  │
                    │  CDN + WAF + DDoS    │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  API Gateway 集群     │
                    │  Spring Cloud Gateway │
                    │  (或 Higress/APISIX)  │
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
   ┌──────▼──────┐     ┌──────▼──────┐     ┌──────▼──────┐
   │  业务服务集群  │     │  业务服务集群  │     │  业务服务集群  │
   │  (Spring Boot│     │  (Spring Boot│     │  (Spring Boot│
   │   3.x + JDK  │     │   3.x + JDK  │     │   3.x + JDK  │
   │   21/24)     │     │   21/24)     │     │   21/24)     │
   └──────┬──────┘     └──────┬──────┘     └──────┬──────┘
          │                    │                    │
          └────────────────────┼────────────────────┘
                               │
        ┌──────────┬───────────┼───────────┬──────────┐
        │          │           │           │          │
   ┌────▼───┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐
   │ Nacos  │ │ Redis  │ │ MySQL  │ │ Rocket │ │  ES /  │
   │ 3.x    │ │ Cluster│ │ Group  │ │  MQ    │ │OpenSearch│
   │注册/配置│ │ 集群    │ │ 集群    │ │ 5.x    │ │ 8.x    │
   └────────┘ └────────┘ └────────┘ └────────┘ └────────┘
                               │
   ┌───────────────────────────┼───────────────────────────┐
   │            可观测性 (Observability)                      │
   │  Micrometer + Prometheus + Grafana                      │
   │  OpenTelemetry (替代 Sleuth+Zipkin)                     │
   │  Loki + Promtail (替代 ELK)                             │
   │  Alertmanager + PagerDuty/飞书/钉钉                      │
   └─────────────────────────────────────────────────────────┘
                               │
   ┌───────────────────────────┼───────────────────────────┐
   │            CI/CD (GitOps)                               │
   │  GitLab/GitHub → ArgoCD → Kubernetes (多环境)           │
   │  Helm Charts + Kustomize                                │
   │  Harbor 私有镜像仓库                                     │
   └─────────────────────────────────────────────────────────┘
```

#### 核心升级点

| 原组件 | 升级为 | 理由 |
|--------|--------|------|
| Ribbon | Spring Cloud LoadBalancer | Ribbon 已废弃，LoadBalancer 是官方替代 |
| Sleuth + Zipkin | **OpenTelemetry** | 2026 年事实标准，CNCF 毕业项目，统一 Metrics/Traces/Logs |
| ELK | **Grafana Loki + Promtail** | 轻量级日志方案，与 Grafana 原生集成，成本低 |
| RabbitMQ Mirror | **RocketMQ 5.x** 或 **Kafka** | 更好的集群管理和事务消息支持 |
| Jenkins | **ArgoCD (GitOps)** | 声明式、可审计、K8s 原生 |
| MySQL 主从 | **TiDB** 或 **MySQL Group Replication** | 分布式能力更强，弹性扩展 |
| JDK 8/11 | **JDK 21 (LTS) / JDK 24** | 虚拟线程 (Project Loom) 大幅提升并发性能 |
| Spring Boot 2.x | **Spring Boot 3.3+** | AOT 编译、GraalVM 原生镜像、虚拟线程支持 |
| Nacos 1.x | **Nacos 3.x** | 更好的性能和安全性 |
| Feign | **Spring Cloud OpenFeign 4.x** 或 **gRPC** | 高性能内部通信可选 gRPC |

#### 关键技术亮点

- **虚拟线程 (Virtual Threads)** — JDK 21 的 Project Loom，以同步写法实现异步性能，Tomcat 可轻松处理百万并发
- **AOT + GraalVM Native Image** — 微服务启动时间从秒级降到毫秒级，内存占用减少 60%+
- **OpenTelemetry** — 统一的可观测性标准，一次接入覆盖 Traces、Metrics、Logs
- **GitOps** — ArgoCD 自动同步 Git 仓库与 K8s 集群状态，审计友好

---

### 方案 B：Service Mesh 架构（适合大规模 / 多语言团队）

> 适合：50+ 微服务，多语言栈，对流量治理有极高要求

```
┌────────────────────────────────────────────────────────────────────┐
│                       用户终端                                      │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                   ┌──────────▼──────────┐
                   │  API Gateway         │
                   │  Higress / APISIX    │
                   │  (云原生网关)          │
                   └──────────┬──────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
   ┌──────▼──────┐    ┌──────▼──────┐    ┌──────▼──────┐
   │  Java 服务   │    │  Go 服务     │    │  Node 服务   │
   │  ┌─────────┐ │    │  ┌─────────┐ │    │  ┌─────────┐ │
   │  │ App     │ │    │  │ App     │ │    │  │ App     │ │
   │  ├─────────┤ │    │  ├─────────┤ │    │  ├─────────┤ │
   │  │ Envoy   │ │    │  │ Envoy   │ │    │  │ Envoy   │ │
   │  │ Sidecar │ │    │  │ Sidecar │ │    │  │ Sidecar │ │
   │  └─────────┘ │    │  └─────────┘ │    │  └─────────┘ │
   └──────────────┘    └──────────────┘    └──────────────┘
          │                   │                   │
          └───────────────────┼───────────────────┘
                              │
              ┌───────────────▼───────────────┐
              │     Istio / Cilium Service Mesh │
              │     控制平面 (Control Plane)      │
              │     流量管理 / 安全 / 策略         │
              └───────────────┬───────────────┘
                              │
   ┌──────────┬───────────┬───┴───────┬──────────┐
   │          │           │           │          │
   │  Nacos/  │  Redis    │  TiDB/    │ RocketMQ │  OpenSearch
   │  Consul  │  Cluster  │ CockroachDB│ / Kafka  │
   └──────────┘           └───────────┘          └──────────┘
```

#### 核心组件

| 组件 | 选型 | 说明 |
|------|------|------|
| **Service Mesh** | Istio 1.x / Cilium | 流量管理、mTLS、灰度发布、熔断全部下沉到 Mesh 层 |
| **API Gateway** | Higress / Apache APISIX | 云原生网关，性能远超 Spring Cloud Gateway |
| **数据库** | TiDB / CockroachDB | 分布式 NewSQL，自动分片，弹性扩缩 |
| **消息队列** | Kafka / RocketMQ 5.x | 高吞吐，事件驱动架构基础 |
| **可观测性** | OpenTelemetry + Tempo + Loki + Grafana | 全链路可观测，统一 Grafana 看板 |

#### 优势

- **语言无关** — Mesh 层统一处理服务发现、负载均衡、熔断、限流，业务代码零侵入
- **灰度发布** — 基于 Istio VirtualService 实现金丝雀发布、蓝绿部署
- **安全** — 自动 mTLS 加密，细粒度访问策略 (AuthorizationPolicy)
- **多语言友好** — Java / Go / Node / Python 服务均可纳入同一 Mesh

---

### 方案 C：Serverless + 事件驱动架构（前沿探索）

> 适合：新启动项目，团队拥抱云原生，追求极致弹性

```
┌──────────────────────────────────────────────────────┐
│                  用户终端                               │
└────────────────────────┬─────────────────────────────┘
                         │
              ┌──────────▼──────────┐
              │  API Gateway         │
              │  (云厂商托管)         │
              └──────────┬──────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
 ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐
 │  Serverless │  │  Serverless │  │  Serverless │
 │  Functions  │  │  Functions  │  │  Functions  │
 │  (业务逻辑)  │  │  (业务逻辑)  │  │  (业务逻辑)  │
 └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
        │                │                │
        └────────────────┼────────────────┘
                         │
    ┌────────────────────▼────────────────────┐
    │         Event Bus / Event Bridge         │
    │         (事件总线 - 异步解耦)              │
    └────┬──────────┬──────────┬──────────────┘
         │          │          │
   ┌─────▼───┐ ┌───▼─────┐ ┌─▼──────────┐
   │ 托管     │ │ 托管     │ │ 托管        │
   │ 数据库   │ │ 缓存     │ │ 对象存储    │
   │(PolarDB/ │ │(Redis   │ │(S3/OSS)   │
   │ PlanetScale)│ Cloud)  │ │            │
   └──────────┘ └─────────┘ └────────────┘
```

#### 核心组件

| 组件 | 选型 | 说明 |
|------|------|------|
| **计算** | AWS Lambda / 阿里云 FC / Knative | 按需付费，零运维 |
| **事件总线** | EventBridge / Kafka Serverless | 事件驱动，解耦服务 |
| **数据库** | PlanetScale / PolarDB Serverless | 按需扩缩的分布式数据库 |
| **缓存** | Redis Cloud / DynamoDB | 托管缓存服务 |
| **前端** | Next.js on Vercel / Cloudflare Pages | Edge 渲染，全球加速 |

#### 优势与限制

- **优势**：零运维、极致弹性、按量付费
- **限制**：冷启动延迟、厂商锁定、调试复杂度高、长连接场景不适用

---

## 三、技术选型对比总表

| 维度 | 方案 A (Spring Cloud 升级) | 方案 B (Service Mesh) | 方案 C (Serverless) |
|------|---------------------------|----------------------|---------------------|
| **迁移成本** | 低（平滑升级） | 高（架构重构） | 极高（全部重写） |
| **学习曲线** | 低 | 高（Istio/K8s 深入） | 中 |
| **团队规模** | 5-20 人 | 20-100+ 人 | 3-10 人 |
| **微服务数量** | 10-50 | 50-200+ | 不限（按函数粒度） |
| **运维复杂度** | 中 | 高 | 低 |
| **弹性能力** | 中（依赖 K8s HPA） | 高 | 极高 |
| **多语言支持** | 弱（Java 为主） | 强 | 强 |
| **厂商锁定** | 低 | 低 | 高 |
| **推荐场景** | 现有项目升级 | 大规模微服务 | 新项目/创新业务 |

---

## 四、最终推荐方案（学习优先，技术选型最优解）

> 以下选型原则：**不考虑历史包袱，每项选当前最流行 / 最值得学 / 面试最加分的技术**。

### 4.0 技术选型决策矩阵

> 每个维度列出 2-3 个候选，★ 为最终推荐

| 领域 | 候选 A | 候选 B | 候选 C | **推荐 & 理由** |
|------|--------|--------|--------|----------------|
| **编程语言** | Java (JDK 21 LTS) ★ | Go 1.24 | Rust | **Java 21** — 生态最成熟，就业最广，虚拟线程革命性提升 |
| **框架** | Spring Boot 3.x ★ | Quarkus | Micronaut | **Spring Boot 3.4+** — Java 生态绝对主流，AOT + GraalVM 原生镜像 |
| **注册/配置** | Nacos 3.x ★ | Consul | etcd + Apollo | **Nacos 3.x** — 国内最流行，注册+配置一体化，学习资源最丰富 |
| **网关** | Higress ★ | APISIX | Spring Cloud Gateway | **Higress** — 阿里开源云原生网关，Envoy 内核，性能是 SCG 的 3 倍+ |
| **服务间通信** | OpenFeign + gRPC ★ | Dubbo 3.x | Connect RPC | **OpenFeign（HTTP）+ gRPC（高性能）** — 两种都学，面试高频考点 |
| **负载均衡** | Spring Cloud LoadBalancer ★ | — | — | **LoadBalancer** — Ribbon 废弃后的唯一官方替代 |
| **熔断限流** | Sentinel 1.8+ ★ | Resilience4j | — | **Sentinel** — 国内主流，控制台可视化，功能全面 |
| **消息队列** | Kafka 3.x ★ | RocketMQ 5.x | Pulsar | **Kafka** — 全球最流行，大数据/事件驱动必学；**RocketMQ** 国内金融场景广泛，建议两个都接触 |
| **关系数据库** | MySQL 8.x ★ + TiDB 7.x ★ | PostgreSQL 17 | — | **MySQL 8** 基础必修 + **TiDB** 分布式 NewSQL 加分项 |
| **ORM** | MyBatis-Plus 3.5+ ★ | Spring Data JPA | jOOQ | **MyBatis-Plus** — 国内最流行；学有余力了解 **jOOQ**（类型安全 SQL） |
| **缓存** | Redis 7.x Cluster ★ | KeyDB | DragonflyDB | **Redis 7** — 绝对主流，Cluster 模式必须掌握 |
| **搜索引擎** | Elasticsearch 8.x ★ | OpenSearch | Meilisearch | **Elasticsearch 8** — 全文检索行业标准，电商搜索必备 |
| **对象存储** | MinIO ★ | 阿里云 OSS | AWS S3 | **MinIO** — S3 兼容协议，可本地自建，学习成本零 |
| **分布式事务** | Seata 2.x ★ | — | — | **Seata** — 阿里开源，AT/TCC/Saga 四种模式都要学 |
| **链路追踪** | OpenTelemetry ★ | SkyWalking | Jaeger | **OpenTelemetry** — CNCF 毕业项目，2026 事实标准，统一 Metrics/Traces/Logs |
| **日志系统** | Grafana Loki ★ | ELK Stack | VictoriaLogs | **Loki** — 轻量，与 Grafana 原生集成；ELK 了解即可（生产仍有大量在用） |
| **监控** | Prometheus + Grafana ★ | Datadog | — | **Prometheus + Grafana** — 开源监控事实标准 |
| **告警** | Alertmanager ★ | Grafana Alerting | PagerDuty | **Alertmanager** — Prometheus 生态标配 |
| **容器编排** | Kubernetes (K8s) ★ | Docker Swarm | — | **K8s** — 云原生基石，必须深入掌握 |
| **CI/CD** | GitHub Actions + ArgoCD ★ | GitLab CI + Flux | Jenkins | **GitHub Actions (CI) + ArgoCD (CD/GitOps)** — 最现代的组合 |
| **镜像仓库** | Harbor ★ | Docker Hub | GHCR | **Harbor** — 企业级私有仓库标配 |
| **IaC** | Terraform ★ | Pulumi | Crossplane | **Terraform** — IaC 事实标准，多云必备 |
| **认证授权** | Spring Security 6 + OAuth 2.1 ★ | Casdoor | Keycloak | **Spring Security 6** 框架必学，**OAuth 2.1 + OIDC** 协议必学 |
| **API 文档** | SpringDoc OpenAPI 3 ★ | — | — | **SpringDoc** — Swagger UI 自动生成，开发体验好 |
| **前端** | Vue 3 + TypeScript ★ | React 19 + Next.js | — | **Vue 3**（国内主流）或 **React + Next.js**（全球主流），建议主攻一个，了解另一个 |

---

### 4.1 整体架构图（推荐方案）

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    用户终端 (Web / App / 小程序)                           │
│         前端：Vue 3 + TypeScript + Vite  或  React 19 + Next.js          │
└───────────────────────────────────┬──────────────────────────────────────┘
                                    │
                       ┌────────────▼────────────┐
                       │  Cloudflare CDN + WAF    │
                       │  (DDoS 防护 + 全球加速)    │
                       └────────────┬────────────┘
                                    │
                       ┌────────────▼────────────┐
                       │  Higress 云原生网关集群    │
                       │  (Envoy 内核 + Wasm 插件) │
                       │  动态路由 / 限流 / 认证    │
                       └────────────┬────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
 ┌──────▼──────┐            ┌──────▼──────┐            ┌──────▼──────┐
 │  商品服务     │            │  订单服务     │            │  用户服务     │
 │  Spring Boot │            │  Spring Boot │            │  Spring Boot │
 │  3.4 + JDK21│            │  3.4 + JDK21│            │  3.4 + JDK21│
 │  虚拟线程     │            │  虚拟线程     │            │  虚拟线程     │
 └──────┬──────┘            └──────┬──────┘            └──────┬──────┘
        │                           │                           │
        └─────────── Feign/gRPC ────┼──── Seata 分布式事务 ─────┘
                                    │
        ┌──────────┬────────────┬───┴──────┬──────────┬──────────┐
        │          │            │          │          │          │
   ┌────▼───┐ ┌───▼────┐ ┌────▼────┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐
   │ Nacos  │ │ Redis  │ │ MySQL 8 │ │ Kafka  │ │  ES 8  │ │ MinIO  │
   │ 3.x    │ │ 7.x    │ │ + TiDB  │ │ 3.x    │ │        │ │        │
   │注册/配置│ │Cluster │ │ 集群     │ │ 集群    │ │ 搜索    │ │ 对象存储│
   └────────┘ └────────┘ └─────────┘ └────────┘ └────────┘ └────────┘

   ┌─────────────────────────────────────────────────────────────────────┐
   │                    可观测性 (Observability)                           │
   │   OpenTelemetry → Prometheus + Grafana (监控)                        │
   │   OpenTelemetry → Grafana Tempo (链路追踪)                           │
   │   OpenTelemetry → Grafana Loki (日志)                                │
   │   Alertmanager → 钉钉 / 飞书 / 企业微信 Webhook                       │
   └─────────────────────────────────────────────────────────────────────┘

   ┌─────────────────────────────────────────────────────────────────────┐
   │                    CI/CD (GitOps)                                    │
   │   GitHub Actions (CI: build + test + scan + push image)             │
   │   ArgoCD (CD: Git repo as source of truth, auto-sync to K8s)        │
   │   Helm 3 + Kustomize (应用包管理)                                    │
   │   Harbor (私有镜像仓库) + Trivy (镜像安全扫描)                         │
   │   Terraform (IaC: 云资源声明式管理)                                   │
   └─────────────────────────────────────────────────────────────────────┘
```

---

### 4.2 核心技术亮点（学习重点）

以下是在 2026 年最值得投入时间学习的技术特性：

#### 1. JDK 21 虚拟线程 (Project Loom)
```java
// 一行配置启用虚拟线程 (Spring Boot 3.4)
spring.threads.virtual.enabled=true

// 效果：Tomcat 从 200 线程 → 无上限虚拟线程
// 以同步写法获得异步性能，并发能力提升 10-100 倍
```
**学习价值**：这是 Java 近 10 年最重要的特性，彻底改变了 Java 并发编程范式。

#### 2. GraalVM Native Image (AOT)
```bash
# 将 Spring Boot 应用编译为原生二进制
mvn -Pnative native:compile

# 启动时间：3秒 → 50毫秒，内存：200MB → 30MB
# 适合 Serverless、K8s 快速扩缩容
```
**学习价值**：云原生时代 Java 启动慢的终极解决方案。

#### 3. OpenTelemetry 统一可观测性
```java
// 一套 SDK 覆盖 Traces + Metrics + Logs
// 替代 Sleuth + Zipkin + Micrometer 的零散组合
OpenTelemetry otel = OpenTelemetrySdk.builder()
    .setTracerProvider(...)
    .setMeterProvider(...)
    .build();
```
**学习价值**：CNCF 毕业项目，可观测性领域的事实标准。

#### 4. GitOps 工作流
```
Developer → git push → GitHub Actions (CI)
                          ├── 单元测试
                          ├── 构建镜像
                          ├── Trivy 安全扫描
                          └── 推送 Harbor
                                    ↓
ArgoCD 检测 Git 变更 → 自动 Sync → K8s Rolling Update
```
**学习价值**：声明式、版本化、可审计的部署范式，替代传统 Jenkins。

#### 5. Kubernetes 核心能力
- **Deployment + HPA** — 自动扩缩容
- **Service + Ingress** — 服务暴露与负载均衡
- **ConfigMap + Secret** — 配置与密钥管理
- **RBAC + NetworkPolicy** — 安全策略
- **Helm Charts** — 应用打包与发布

---

### 4.3 各模块详细技术栈清单

#### 前端层
| 技术 | 说明 |
|------|------|
| **Vue 3 + Composition API** | 或 React 19，国内选 Vue，国际选 React |
| **TypeScript 5.x** | 类型安全，现代前端必备 |
| **Vite 6** | 极速构建工具（Vue 生态）|
| **Pinia** | Vue 3 状态管理（替代 Vuex）|
| **Tailwind CSS 4** | 原子化 CSS，开发效率极高 |
| **Nuxt 3** (可选) | SSR/SSG 框架，SEO 友好 |

#### 网关层
| 技术 | 说明 |
|------|------|
| **Higress** | 阿里开源，基于 Envoy，支持 Wasm 插件扩展 |
| 备选：**Apache APISIX** | 高性能 Lua/Go 网关，插件生态丰富 |
| 备选：**Spring Cloud Gateway** | Java 生态内最熟，学习门槛最低 |

#### 微服务核心
| 技术 | 版本 | 说明 |
|------|------|------|
| **JDK** | 21 LTS | 虚拟线程 + 模式匹配 + Record 类 |
| **Spring Boot** | 3.4+ | 最新稳定版 |
| **Spring Cloud** | 2024.x | LoadBalancer、OpenFeign |
| **Spring Cloud Alibaba** | 2023.x+ | Nacos、Sentinel 适配 Spring Boot 3 |
| **gRPC** | 1.6x | 高性能内部通信（Protobuf 序列化）|
| **Seata** | 2.x | 分布式事务 AT/TCC/Saga |

#### 数据层
| 技术 | 说明 |
|------|------|
| **MySQL 8.x** | 基础关系数据库，InnoDB 集群 |
| **TiDB 7.x** (进阶) | 分布式 NewSQL，HTAP 混合负载 |
| **Redis 7.x Cluster** | 分布式缓存，掌握 Cluster 原理 |
| **MyBatis-Plus 3.5+** | ORM，国内主流 |
| **Flyway** | 数据库迁移版本管理 |
| **ShardingSphere 5.x** | 分库分表中间件（如不用 TiDB）|

#### 消息与搜索
| 技术 | 说明 |
|------|------|
| **Kafka 3.x** | 全球最流行消息系统，事件驱动必学 |
| **RocketMQ 5.x** (补充) | 国内金融/电商场景广泛，事务消息强 |
| **Elasticsearch 8.x** | 全文检索 + 日志存储双用 |
| **MinIO** | S3 兼容对象存储，本地自建零成本 |

#### 可观测性
| 技术 | 说明 |
|------|------|
| **OpenTelemetry** | 统一采集 Traces/Metrics/Logs（替代 Sleuth+Zipkin）|
| **Prometheus** | 指标存储与 PromQL 查询 |
| **Grafana** | 可视化大盘 |
| **Grafana Loki** | 轻量日志（替代 ELK，学习 ELK 了解即可）|
| **Grafana Tempo** | 分布式追踪存储（替代 Zipkin）|
| **Alertmanager** | 告警路由 |

#### 基础设施 & CI/CD
| 技术 | 说明 |
|------|------|
| **Docker** | 容器化基础 |
| **Kubernetes (K8s)** | 容器编排，云原生基石 |
| **Helm 3** | K8s 应用包管理 |
| **ArgoCD** | GitOps 持续部署 |
| **GitHub Actions** | CI 流水线 |
| **Harbor** | 企业级私有镜像仓库 |
| **Terraform** | IaC 基础设施即代码 |
| **Trivy** | 镜像漏洞扫描 |

#### 安全
| 技术 | 说明 |
|------|------|
| **Spring Security 6.x** | 认证授权框架 |
| **OAuth 2.1 + OIDC** | 标准认证协议 |
| **JWT (nimbus-jose)** | Token 生成与验证 |
| **OPA / Kyverno** | K8s 策略引擎 |

---

### 4.4 学习路径建议

```
第一阶段 (基础功)          第二阶段 (微服务核心)        第三阶段 (高阶能力)
┌────────────────┐      ┌────────────────┐      ┌────────────────┐
│ JDK 21 新特性   │      │ Spring Cloud   │      │ K8s 深入       │
│ Spring Boot 3  │  →   │ Nacos + Feign  │  →   │ GitOps ArgoCD  │
│ MySQL + Redis  │      │ Sentinel 限流   │      │ OpenTelemetry  │
│ MyBatis-Plus   │      │ Kafka 消息驱动   │      │ TiDB 分布式 DB  │
│ Vue 3 + TS     │      │ ES 全文检索     │      │ Service Mesh    │
│ Docker 基础    │      │ Seata 分布式事务 │      │ gRPC 高性能通信  │
└────────────────┘      └────────────────┘      └────────────────┘
```

| 阶段 | 时长建议 | 核心产出 |
|------|---------|---------|
| **第一阶段：基础功** | 1-2 个月 | 能独立搭建单体 Spring Boot 3 应用，CRUD + 缓存 + 前端联调 |
| **第二阶段：微服务核心** | 2-3 个月 | 拆分微服务，跑通注册发现、配置中心、消息队列、分布式事务 |
| **第三阶段：高阶能力** | 2-4 个月 | K8s 部署、GitOps 流水线、全链路可观测、性能调优 |

---

## 五、架构演进路线图

```
Phase 1 (1-2 月)     Phase 2 (2-3 月)       Phase 3 (3-6 月)
┌──────────────┐    ┌──────────────┐      ┌──────────────┐
│ JDK 21 升级   │    │ 可观测性迁移   │      │ GitOps 落地   │
│ Spring Boot 3 │ →  │ OpenTelemetry │  →   │ ArgoCD       │
│ Ribbon 替换   │    │ Loki + Tempo  │      │ IaC          │
│ Feign 升级    │    │ 告警体系重建   │      │ 灰度发布能力  │
└──────────────┘    └──────────────┘      └──────────────┘
```

| 阶段 | 目标 | 关键动作 |
|------|------|---------|
| **Phase 1** | 核心框架升级 | JDK 21、Spring Boot 3、替换 Ribbon、升级 Nacos |
| **Phase 2** | 可观测性现代化 | 接入 OpenTelemetry、部署 Loki/Tempo、重建告警规则 |
| **Phase 3** | CI/CD 与运维现代化 | GitOps (ArgoCD)、IaC (Terraform)、金丝雀发布 |
| **Phase 4** (可选) | 数据层升级 | 评估 TiDB、引入 RocketMQ 5.x、分库分表优化 |

---

## 六、总结

### 原架构 vs 2026 推荐架构对照

| 原架构（2020年前） | 2026 推荐（学习最优） | 关键理由 |
|-------------------|----------------------|---------|
| Ribbon | Spring Cloud LoadBalancer | Ribbon 已废弃，面试不会再问 |
| Sleuth + Zipkin | **OpenTelemetry** | CNCF 事实标准，统一可观测 |
| ELK (Logstash 重) | **Grafana Loki** | 轻量，与 Grafana 一体化 |
| Spring Cloud Gateway | **Higress / APISIX** | 云原生网关，性能 3 倍+，学习 Envoy 生态 |
| Jenkins | **GitHub Actions + ArgoCD** | GitOps 范式，2026 主流 |
| RabbitMQ Mirror | **Kafka** + RocketMQ | Kafka 全球最流行，RocketMQ 国内金融首选 |
| MySQL 主从 | **MySQL 8 + TiDB** | TiDB 是分布式 DB 的国内代表 |
| JDK 8/11 | **JDK 21 LTS** | 虚拟线程是 Java 10 年来最大升级 |
| Spring Boot 2.x | **Spring Boot 3.4+** | AOT 原生镜像，Observability API |
| Nacos 1.x | **Nacos 3.x** | 适配 Spring Boot 3，性能与安全提升 |
| Feign | **OpenFeign + gRPC** | 两种都学，gRPC 是高性能方向 |
| OAuth 2.0 | **OAuth 2.1 + OIDC** | 协议已演进，2.0 部分模式已弃用 |

### 学习价值排序（面试加分项）

1. **Spring Boot 3 + JDK 21 虚拟线程** — 每个 Java 面试都会问
2. **Kafka 消息队列** — 分布式系统标配，高频面试题
3. **Redis Cluster 原理** — 缓存架构必考
4. **Kubernetes + Docker** — 云原生基础，运维/开发都要会
5. **分布式事务 (Seata)** — 微服务架构核心难点，面试区分度高
6. **OpenTelemetry 可观测性** — 大厂必备能力，中厂加分项
7. **GitOps (ArgoCD)** — 现代 DevOps 的标配工作流
8. **Elasticsearch 搜索** — 电商系统核心能力
9. **TiDB 分布式数据库** — 了解 NewSQL 是加分项
10. **gRPC 高性能通信** — 进阶能力，大厂内部常用
