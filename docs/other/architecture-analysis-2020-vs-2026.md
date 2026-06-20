# 谷粒商城架构演进分析 — 从 2020 到 2026

> 本文档基于「谷粒商城-微服务架构图」（约 2020 年技术栈）进行分析，并给出 2026 年基于最新技术栈的推荐架构与技术选型方案。
> 面向**学习目的**，技术选型以「最优 / 最流行 / 最有学习价值」为原则。

---

## 目录

- [一、2020 原始架构回顾](#一2020-原始架构回顾)
- [二、技术演进分析：哪些已过时](#二技术演进分析哪些已过时)
- [三、2026 推荐方案一：经典微服务升级版（首选）](#三2026-推荐方案一经典微服务升级版首选)
- [四、2026 备选方案二：云原生 Service Mesh 版（进阶）](#四2026-备选方案二云原生-service-mesh-版进阶)
- [五、两个方案对比](#五两个方案对比)
- [六、核心技术选型对照表](#六核心技术选型对照表)
- [七、从零开发学习路线](#七从零开发学习路线)
- [八、附录：关键技术版本参考](#八附录关键技术版本参考)

---

## 一、2020 原始架构回顾

原始架构（详见 `legacy-architecture-details.md`）采用 **Spring Cloud Hoxton + Spring Boot 2.x + Spring Cloud Alibaba** 体系，分为六大层：

| 层次 | 核心组件 |
|------|---------|
| 接入层 | DNS → CDN → WAF → Nginx → Spring Cloud Gateway (Webflux) |
| 服务治理 | Nacos（注册+配置）、OAuth2、Spring Security、Ribbon、Feign、Sentinel |
| 业务服务 | Spring Boot 多实例集群（用户/商品/订单/库存/购物车/优惠券等） |
| 数据存储 | MySQL 主从、Redis（Sentinel+Shard）、RabbitMQ（Mirror）、Elasticsearch、OSS |
| 可观测性 | Sleuth+Zipkin、Prometheus、Grafana、Alertmanager、ELK（LogStash+ES+Kibana） |
| DevOps | GitHub → Jenkins Pipeline → Docker → K8s |

这是一套**非常经典且完整**的微服务架构，在 2020 年前后是国内企业级微服务的事实标准。但 6 年过去，其中相当一部分技术已被废弃或被更优方案取代。

---

## 二、技术演进分析：哪些已过时

### 2.1 已废弃 / 停止维护（必须替换）

| 原始技术 | 状态 | 替代方案 | 原因 |
|---------|------|---------|------|
| **Spring Boot 2.x** | 2023 年已 EOL | Spring Boot 3.x | 基于 Spring 6，要求 Java 17+，全面拥抱 Jakarta EE |
| **Spring Cloud Hoxton** | 已停止维护 | Spring Cloud 2024.x | 配套 Spring Boot 3.x |
| **Ribbon** | **已废弃**，Spring Cloud 官方移除 | Spring Cloud LoadBalancer | Netflix OSS 全家桶停更，Ribbon 不再维护 |
| **Hystrix** | **已废弃**，Netflix 停止维护 | Resilience4j | Hystrix 不再更新，Resilience4j 是官方推荐的 Java 函数式熔断库 |
| **Sleuth** | **已废弃**，Spring Cloud 2022 起移除 | Micrometer Tracing + OpenTelemetry | Sleuth 被官方废弃，由 Micrometer Tracing 接替 |
| **Spring Cloud Netflix OSS 全家桶** | 整体停更 | Spring Cloud Alibaba / 官方组件 | Netflix OSS 在 2018 年后陆续进入维护模式 |

### 2.2 仍可用但有更优选择（建议升级）

| 原始技术 | 现状 | 升级建议 | 理由 |
|---------|------|---------|------|
| **Java 8** | 早已过时 | **Java 21 (LTS)** | Java 21 支持虚拟线程（Loom），对高并发微服务是革命性提升；也是 Spring Boot 3 的最低要求 |
| **Nginx 作为业务网关** | 仍可用 | **APISIX** 或保留 Nginx 做纯反向代理 | APISIX 是云原生 API 网关，动态路由、插件生态远优于 Nginx 原生能力 |
| **Redis Sentinel+Shard** | 可用但落后 | **Redis Cluster（原生集群）** | Redis 7 原生 Cluster 更成熟，无需 Sentinel+Shard 组合，运维更简单 |
| **RabbitMQ** | 仍主流 | **RocketMQ 5.x**（商城场景） | 商城有大量事务消息、延迟消息（订单超时）、削峰需求，RocketMQ 原生支持事务消息和延迟消息，比 RabbitMQ 更适合电商 |
| **ELK（LogStash）** | 仍可用但重 | **Loki + Promtail** 或 **EFK（Filebeat）** | LogStash 资源占用高，Loki 是 Grafana 生态的轻量日志方案，与 Prometheus/Grafana 统一；若需全文检索日志可用 EFK |
| **Elasticsearch** | 仍主流 | **OpenSearch 2.x** | ES 许可证变更（SSPL）后，OpenSearch 是 AWS 主导的开源分支，完全 Apache 2.0，社区活跃 |
| **OSS（阿里云）** | 商业闭源 | **MinIO** | 学习项目用 MinIO 自建对象存储，开源免费，API 兼容 S3 |
| **Jenkins** | 仍可用但传统 | **GitHub Actions + ArgoCD (GitOps)** | Jenkins 运维重，GitHub Actions 云原生 CI，ArgoCD 实现声明式 GitOps 部署 |
| **OAuth2 认证中心（自研）** | 维护成本高 | **Spring Authorization Server** | Spring 官方推出的 OAuth2 授权服务器框架，替代已废弃的 Spring Security OAuth |

### 2.3 仍为主流（保留或小幅升级）

| 技术 | 说明 |
|------|------|
| **Nacos** | 仍是国内最流行的注册+配置中心，升级到 2.4 |
| **Sentinel** | Alibaba 持续维护，仍是流控熔断首选 |
| **OpenFeign** | 仍在 Spring Cloud 中，可继续用；也可了解 Spring 6 HTTP Interface |
| **Spring Cloud Gateway** | 仍是主流网关，升级到 4.x |
| **MySQL** | 仍是关系型数据库首选，升级到 8.4 |
| **Prometheus + Grafana** | 监控事实标准，保留 |
| **Docker + Kubernetes** | 容器化标准，保留，K8s 升级到 1.30+ |
| **Seata** | 分布式事务仍用 Seata，升级到 2.x |

---

## 三、2026 推荐方案一：经典微服务升级版（首选）

> **推荐理由**：在保留微服务经典架构思想的同时，全面升级到 2026 最新技术栈。学习曲线平缓，技术栈流行度高，社区资料丰富，最适合从零学习微服务架构。

### 3.1 整体技术栈

| 层次 | 技术选型 | 版本 |
|------|---------|------|
| **开发语言** | Java | 21 (LTS，虚拟线程) |
| **核心框架** | Spring Boot | 3.4 |
| **微服务套件** | Spring Cloud | 2024.0 |
| **注册/配置中心** | Nacos | 2.4 |
| **API 网关** | Spring Cloud Gateway + APISIX（可选前置） | 4.x |
| **服务调用** | OpenFeign + Spring Cloud LoadBalancer | — |
| **熔断降级** | Resilience4j（代码级）+ Sentinel（规则级） | 2.x |
| **认证授权** | Spring Authorization Server + Spring Security 6 | 1.3 |
| **分布式事务** | Seata | 2.1 |
| **关系型数据库** | MySQL | 8.4 |
| **分库分表** | Apache ShardingSphere | 5.5 |
| **缓存** | Redis | 7.4 (Cluster) |
| **分布式锁** | Redisson | 3.x |
| **消息队列** | Apache RocketMQ | 5.3 |
| **搜索引擎** | OpenSearch | 2.x |
| **对象存储** | MinIO | 最新稳定版 |
| **任务调度** | XXL-Job | 2.4 |
| **数据同步** | Canal（MySQL → MQ → ES/缓存） | 1.1 |
| **链路追踪** | Micrometer Tracing + OpenTelemetry + Tempo/Jaeger | — |
| **监控** | Prometheus + Grafana + VictoriaMetrics | — |
| **日志** | Loki + Promtail（或 EFK） | — |
| **容器** | Docker + Kubernetes + Helm | K8s 1.30 |
| **CI/CD** | GitHub Actions（CI）+ ArgoCD（GitOps CD） | — |
| **镜像仓库** | Harbor | 2.x |

### 3.2 架构图

见对话中展示的「商城系统推荐架构 (2026) — 经典微服务升级版」架构图。

### 3.3 关键技术选型理由

#### ① 为什么选 Java 21（虚拟线程）
Java 21 是当前 LTS 版本，**虚拟线程（Project Loom）** 是最重要的特性。传统微服务每个请求占用一个平台线程，线程数受限于内存（通常几百到几千）。虚拟线程让一个平台线程可以承载数万个虚拟线程，对 IO 密集型的微服务（如商城大量调用下游服务、查询数据库）是数量级的并发提升，且代码写法不变（仍是同步阻塞式），学习成本极低。

#### ② 为什么选 Spring Boot 3.x
- 基于 Spring 6 + Jakarta EE 9+（`javax.*` → `jakarta.*`）
- 原生支持 GraalVM Native Image（AOT 编译，启动毫秒级，内存占用极低）
- 最低要求 Java 17，推荐 Java 21
- 是 2026 年 Spring 生态的绝对主流

#### ③ 为什么 Ribbon → Spring Cloud LoadBalancer
Ribbon 是 Netflix OSS 的一部分，Netflix 在 2018 年后停止维护，Spring Cloud 官方已将其移除。**Spring Cloud LoadBalancer** 是官方推出的替代品，API 更现代，与 Spring 生态深度集成，是唯一正确的选择。

#### ④ 为什么 Hystrix → Resilience4j
Hystrix 同属 Netflix OSS，已停止开发（仅维护模式）。**Resilience4j** 是 Spring 官方推荐的熔断库，特点：
- 函数式 API，无反射，轻量
- 提供熔断（Circuit Breaker）、舱壁（Bulkhead）、限流（Rate Limiter）、重试（Retry）、超时（Time Limiter）五大能力
- 与 Spring Boot 3 / Spring Cloud 2024 深度集成

> Sentinel 与 Resilience4j 的分工：**Resilience4j 用于代码级细粒度控制**（单个方法的重试、舱壁），**Sentinel 用于规则级流控**（接口 QPS 限流、热点参数限流），两者互补不冲突。

#### ⑤ 为什么 Sleuth → Micrometer Tracing + OpenTelemetry
Sleuth 在 Spring Cloud 2022.0 被正式移除。官方替代方案是 **Micrometer Tracing**，它桥接了 **OpenTelemetry**（CNCF 可观测性标准）。好处：
- 与 Micrometer（指标）、Spring Boot Actuator 统一
- 支持 OpenTelemetry 协议，可对接 Tempo / Jaeger / Zipkin / SkyWalking 等任意后端
- 未来不会被锁定在单一厂商

#### ⑥ 为什么 RabbitMQ → RocketMQ
商城场景的核心消息需求：
- **事务消息**：下单扣库存、支付成功改订单状态（需要分布式事务保证）
- **延迟消息**：订单 30 分钟未支付自动取消
- **削峰填谷**：秒杀场景

| 能力 | RabbitMQ | RocketMQ |
|------|----------|----------|
| 事务消息 | 需自行实现（确认机制+本地表） | **原生支持**（半消息+回查） |
| 延迟消息 | TTL+死信队列（有顺序问题） | **原生多级延迟**（1s/5s/10s...2h） |
| 消息堆积 | 一般（内存受限） | **极强**（磁盘顺序写） |
| 吞吐量 | 万级 TPS | 十万级 TPS |
| 顺序消息 | 需特殊设计 | **原生支持** |

对电商学习项目，RocketMQ 的事务消息和延迟消息能力是**杀手级特性**，能省去大量自行实现的复杂逻辑。

#### ⑦ 为什么 ES → OpenSearch
2021 年 Elastic 公司将 Elasticsearch 许可证从 Apache 2.0 改为 SSPL（非开源），AWS 随后 fork 出 **OpenSearch**，完全 Apache 2.0 开源。两者 API 高度兼容，OpenSearch 社区活跃，Linux 基金会托管，是学习场景的开源首选。

#### ⑧ 为什么 Jenkins → GitHub Actions + ArgoCD
- **Jenkins**：单体架构，运维成本高，UI 老旧，插件管理复杂
- **GitHub Actions**：云原生 CI，YAML 声明式，与代码托管深度集成，零运维
- **ArgoCD**：GitOps 模式 CD，K8s 原生，声明式部署，可视化发布流程，支持渐进式发布（Argo Rollouts）

GitOps 的核心理念：**Git 仓库是唯一事实来源**，集群状态自动向 Git 声明状态收敛，比传统 push 模式更安全、可审计。

### 3.4 业务服务划分建议

| 服务 | 职责 | 数据库 |
|------|------|--------|
| `mall-auth` | 认证授权中心（OAuth2 登录、Token 颁发） | `mall_auth` |
| `mall-member` | 会员中心（注册、登录、收货地址、积分） | `mall_member` |
| `mall-product` | 商品中心（SPU/SKU、分类、品牌、属性） | `mall_product` |
| `mall-search` | 搜索服务（商品检索、聚合筛选，对接 OpenSearch） | — |
| `mall-cart` | 购物车（Redis Hash 存储） | — |
| `mall-order` | 订单中心（下单、订单状态、超时取消） | `mall_order` |
| `mall-ware` | 库存中心（库存扣减、回滚、预警） | `mall_ware` |
| `mall-coupon` | 营销中心（优惠券、满减、促销） | `mall_coupon` |
| `mall-seckill` | 秒杀服务（独立部署，Redis+RocketMQ 削峰） | `mall_seckill` |
| `mall-third` | 第三方服务（短信、支付、OSS 上传） | — |
| `mall-gateway` | API 网关（统一入口、鉴权、限流） | — |
| `mall-admin` | 后台管理（商品上下架、订单管理、数据看板） | — |

---

## 四、2026 备选方案二：云原生 Service Mesh 版（进阶）

> **适用人群**：已掌握经典微服务，想进一步学习云原生前沿技术。运维复杂度高，但能学到 Service Mesh、GitOps、eBPF 等前沿方向。

### 4.1 与方案一的核心差异

| 维度 | 方案一（经典微服务） | 方案二（Service Mesh） |
|------|-------------------|---------------------|
| 服务治理 | SDK 模式（Sentinel/Resilience4j 嵌入业务代码） | **Sidecar 模式**（Istio 数据面代理，业务代码零侵入） |
| 流量管理 | Spring Cloud Gateway + OpenFeign | **Istio VirtualService + DestinationRule**（声明式流量路由） |
| 熔断限流 | Resilience4j + Sentinel（代码级） | **Envoy 原生熔断 + Istio 限流**（配置级，无需改代码） |
| 链路追踪 | Micrometer Tracing + OpenTelemetry SDK | **Istio 自动注入 Trace Header**（业务无感知） |
| mTLS | 需自行实现服务间加密 | **Istio 自动 mTLS**（双向证书加密，零配置） |
| 金丝雀发布 | Argo Rollouts + 手动配置 | **Istio 权重路由 + Argo Rollouts**（声明式灰度） |
| 网关 | Spring Cloud Gateway | **APISIX / Istio Ingress Gateway** |
| 可观测 | Prometheus + Loki + Tempo | **Istio 集成 Kiali（拓扑图）+ Prometheus + Loki + Tempo** |

### 4.2 额外引入的技术

| 技术 | 作用 |
|------|------|
| **Istio** | Service Mesh 控制面 + Envoy 数据面，接管服务间通信、安全、可观测 |
| **Kiali** | 服务网格拓扑可视化，实时展示服务调用关系图 |
| **APISIX** | 云原生 API 网关，替代 Spring Cloud Gateway 做南北向流量管理 |
| **Argo Rollouts** | 渐进式发布（金丝雀、蓝绿），比 K8s 原生 Deployment 更强大 |
| **Cert-Manager** | 自动证书管理（配合 Istio mTLS） |

### 4.3 方案二适用场景与风险

**优点**：
- 业务代码极简，治理能力下沉到基础设施层
- 学到 Service Mesh 这一云原生核心方向
- 流量治理、安全、可观测能力远超 SDK 模式

**风险/代价**：
- Istio 学习曲线陡峭，运维复杂
- Sidecar 有 1-2ms 的额外延迟和资源开销
- 对学习项目的硬件资源要求更高（至少 16GB 内存跑全套）

> **学习建议**：先用方案一跑通完整商城，再在部分服务上引入 Istio 体验 Service Mesh，循序渐进。

---

## 五、两个方案对比

| 对比项 | 方案一：经典微服务升级版 | 方案二：Service Mesh 版 |
|-------|----------------------|----------------------|
| **学习难度** | ⭐⭐⭐ 中等 | ⭐⭐⭐⭐⭐ 高 |
| **流行度** | ⭐⭐⭐⭐⭐ 国内绝对主流 | ⭐⭐⭐⭐ 前沿趋势，大厂在用 |
| **运维复杂度** | ⭐⭐⭐ 中 | ⭐⭐⭐⭐⭐ 高 |
| **业务代码侵入** | 中（需引入 SDK） | 低（治理下沉 sidecar） |
| **资料丰富度** | ⭐⭐⭐⭐⭐ 极多 | ⭐⭐⭐ 中等 |
| **求职价值** | ⭐⭐⭐⭐⭐ 中小厂/中大厂通用 | ⭐⭐⭐⭐ 大厂云原生方向加分 |
| **硬件要求** | 8GB 内存可跑 | 16GB+ 内存 |
| **推荐学习顺序** | **第一步** | **第二步（进阶）** |

> **结论**：学习项目**强烈建议从方案一开始**。方案一是 2026 年国内微服务的事实标准，覆盖了 90% 的知识点，资料最全，学完即可直接用于面试和工作。方案二作为进阶，在掌握方案一后再拓展。

---

## 六、核心技术选型对照表

### 6.1 2020 vs 2026 全量对照

| 类别 | 2020 原始 | 2026 推荐（方案一） | 变更说明 |
|------|----------|-------------------|---------|
| 开发语言 | Java 8 | **Java 21** | 虚拟线程，LTS |
| 核心框架 | Spring Boot 2.x | **Spring Boot 3.4** | Spring 6 / Jakarta EE |
| 微服务套件 | Spring Cloud Hoxton | **Spring Cloud 2024.0** | 配套 Boot 3 |
| 注册中心 | Nacos 1.x | **Nacos 2.4** | 性能提升 10 倍 |
| 配置中心 | Nacos 1.x | **Nacos 2.4** | 一体化 |
| API 网关 | Spring Cloud Gateway (Webflux) | **Spring Cloud Gateway 4.x** | 响应式升级 |
| 负载均衡 | Ribbon ❌ | **Spring Cloud LoadBalancer** | Ribbon 已废弃 |
| 服务调用 | Feign | **OpenFeign**（+ 可选 Spring 6 HTTP Interface） | 保留 |
| 熔断降级 | Hystrix ❌ | **Resilience4j + Sentinel** | Hystrix 已废弃 |
| 认证授权 | 自研 OAuth2 | **Spring Authorization Server + Spring Security 6** | 官方框架 |
| 分布式事务 | — | **Seata 2.1** | 新增 |
| 数据库 | MySQL 5.7/8.0 | **MySQL 8.4** | LTS |
| 分库分表 | — | **ShardingSphere 5.5** | 新增 |
| 缓存 | Redis Sentinel+Shard | **Redis 7.4 Cluster** | 原生集群 |
| 分布式锁 | — | **Redisson** | 明确 |
| 消息队列 | RabbitMQ Mirror | **RocketMQ 5.3** | 事务消息/延迟消息 |
| 搜索引擎 | Elasticsearch 7.x | **OpenSearch 2.x** | 开源许可 |
| 对象存储 | 阿里云 OSS | **MinIO** | 开源自建 |
| 任务调度 | 自研 | **XXL-Job 2.4** | 开源调度 |
| 数据同步 | — | **Canal** | MySQL→MQ→ES |
| 链路追踪 | Sleuth + Zipkin ❌ | **Micrometer Tracing + OpenTelemetry + Tempo** | Sleuth 已废弃 |
| 监控 | Prometheus | **Prometheus + VictoriaMetrics** | 长期存储增强 |
| 可视化 | Grafana | **Grafana** | 保留 |
| 日志 | ELK (LogStash) | **Loki + Promtail**（或 EFK） | 轻量化 |
| 告警 | Alertmanager | **Alertmanager + Grafana Alerting** | 保留增强 |
| 容器化 | Docker | **Docker** | 保留 |
| 容器编排 | K8s 1.18 | **K8s 1.30 + Helm** | 升级 |
| CI | Jenkins Pipeline ❌ | **GitHub Actions** | 云原生 CI |
| CD | K8s 手动部署 | **ArgoCD (GitOps)** | 声明式 CD |
| 镜像仓库 | Docker Registry | **Harbor** | 安全扫描 |

### 6.2 消息队列选型决策树

```
你的场景是什么？
├── 大数据/日志采集 → Kafka
├── 电商核心业务（事务消息、延迟消息、削峰） → RocketMQ ✅ 推荐
├── 中小系统/复杂路由 → RabbitMQ
└── 多协议/云原生流处理 → Apache Pulsar
```

### 6.3 缓存架构演进

```
2020: Redis Sentinel（高可用） + Shard（分片） → 两套机制叠加，运维复杂
                        ↓
2026: Redis Cluster（原生集群） → 集群内置高可用+分片，统一管理
      + Redis 7 Function（取代 Lua 脚本部分场景）
      + Redis Streams（轻量消息流，可作为 MQ 补充）
```

---

## 七、从零开发学习路线

> 按以下顺序逐步开发，每一阶段都有明确的交付物，避免一次性啃太多。

### 阶段一：环境搭建与单体起步（1-2 周）

**目标**：搭好开发环境，跑通单体版商城核心功能

- [ ] 安装 JDK 21、IDEA、Docker Desktop、Git
- [ ] 用 Docker Compose 启动本地中间件：MySQL 8.4、Redis 7、RocketMQ、OpenSearch、MinIO、Nacos
- [ ] 创建 `mall-product` 单体服务，实现商品 CRUD
- [ ] 集成 MyBatis-Plus + MySQL
- [ ] 集成 Springdoc OpenAPI（Swagger UI）做接口文档

**交付物**：能跑的商品管理单体应用

### 阶段二：微服务化（2-3 周）

**目标**：拆分为微服务，引入服务治理

- [ ] 拆分 `mall-product`、`mall-member`、`mall-order`、`mall-ware` 服务
- [ ] 接入 Nacos 注册中心 + 配置中心
- [ ] 引入 Spring Cloud Gateway 统一入口
- [ ] 用 OpenFeign 实现服务间调用
- [ ] 引入 Spring Cloud LoadBalancer 负载均衡
- [ ] 配置 Sentinel 限流规则

**交付物**：4 个微服务 + 网关，服务间可互调

### 阶段三：核心业务开发（3-4 周）

**目标**：完成商城核心交易闭环

- [ ] 商品中心：SPU/SKU 模型、分类树、属性规格
- [ ] 会员中心：注册登录、JWT Token、收货地址
- [ ] 认证中心：Spring Authorization Server 颁发 Token
- [ ] 购物车：Redis Hash 存储
- [ ] 订单中心：下单流程、库存扣减
- [ ] 接入 RocketMQ：订单超时取消（延迟消息）、异步扣库存（事务消息）
- [ ] 引入 Seata 解决下单扣库存的分布式事务

**交付物**：完整的下单交易流程

### 阶段四：搜索与营销（2-3 周）

**目标**：商品搜索 + 营销活动

- [ ] 用 Canal 同步 MySQL 商品数据到 OpenSearch
- [ ] 实现商品全文搜索、分类聚合、价格筛选
- [ ] 优惠券服务：领券、用券
- [ ] 秒杀服务：Redis 预减库存 + RocketMQ 削峰

**交付物**：搜索功能 + 秒杀活动

### 阶段五：可观测性与运维（2 周）

**目标**：完整的监控、日志、链路追踪体系

- [ ] 接入 Micrometer Tracing + OpenTelemetry，对接 Tempo
- [ ] Prometheus 采集指标 + Grafana 大盘
- [ ] Loki + Promtail 日志聚合
- [ ] Alertmanager 配置告警规则

**交付物**：完整的可观测性体系

### 阶段六：容器化与 CI/CD（2 周）

**目标**：云原生部署

- [ ] 编写多阶段 Dockerfile
- [ ] 编写 Helm Chart
- [ ] 本地用 Kind/Minikube 跑 K8s
- [ ] GitHub Actions CI 流水线（构建+测试+镜像推送 Harbor）
- [ ] ArgoCD GitOps 部署到 K8s

**交付物**：一键部署的云原生商城

### 阶段七（进阶）：Service Mesh 探索（可选）

- [ ] 安装 Istio
- [ ] 将部分服务纳入 Mesh
- [ ] 体验 Kiali 拓扑图、流量治理、mTLS
- [ ] 用 Argo Rollouts 做金丝雀发布

---

## 八、附录：关键技术版本参考

> 以下为 2026 年中各技术的推荐版本（基于编写时的稳定发布情况，实际开发时取最新稳定版）

| 技术 | 推荐版本 | 说明 |
|------|---------|------|
| JDK | 21 (LTS) | 虚拟线程 GA |
| Spring Boot | 3.4.x | 最新稳定大版本 |
| Spring Cloud | 2024.0.x | 配套 Boot 3.4 |
| Spring Cloud Alibaba | 2023.0.x | 配套 Spring Cloud 2023/2024 |
| Nacos | 2.4.x | 注册+配置一体 |
| Sentinel | 1.8.6+ | 流控熔断 |
| Seata | 2.1.x | 分布式事务 |
| MySQL | 8.4 (LTS) | 长期支持版 |
| Redis | 7.4.x | Cluster 模式 |
| RocketMQ | 5.3.x | 事务消息+延迟消息 |
| OpenSearch | 2.x | 开源搜索 |
| ShardingSphere | 5.5.x | 分库分表 |
| Kubernetes | 1.30+ | 容器编排 |
| Istio | 1.22+ | Service Mesh（方案二） |
| ArgoCD | 2.11+ | GitOps CD |

---

## 总结

**一句话选型**：Java 21 + Spring Boot 3.4 + Spring Cloud 2024 + Nacos + Gateway + OpenFeign + LoadBalancer + Resilience4j + Sentinel + Seata + MySQL 8.4 + ShardingSphere + Redis 7 Cluster + RocketMQ 5 + OpenSearch + MinIO + OpenTelemetry + Prometheus + Grafana + Loki + K8s + GitHub Actions + ArgoCD。

这套技术栈是 **2026 年国内微服务开发的事实标准**，每一项都是该领域最流行/最优的选择，覆盖了从开发到运维的全链路。作为学习项目，它能让你掌握当前企业级微服务开发的全部核心技能，直接对标中大厂的技术要求。

> 文档完成于 2026 年 6 月，技术选型基于编写时的最新情况。实际开发时建议取各组件的最新稳定版。
