# 谷粒商城 — 技术选型与架构设计（2026）

> 学习项目，技术选型原则：**最优 / 最流行 / 最值得学**

---

## 一、技术选型总览

### 1.1 全量选型表

| 类别 | 技术选型 | 说明 |
|------|---------|------|
| 开发语言 | **Java 21 (LTS)** | 虚拟线程（Loom），Spring Boot 3 最低要求 |
| 核心框架 | **Spring Boot 3.4** | Spring 6 / Jakarta EE 9+，AOT + GraalVM |
| 微服务套件 | **Spring Cloud 2024.0** | 配套 Boot 3 |
| 注册/配置中心 | **Nacos 2.4** | 性能提升 10 倍，注册+配置一体 |
| API 网关 | **Spring Cloud Gateway 4.x** | 响应式模型，统一入口 |
| 负载均衡 | **Spring Cloud LoadBalancer** | 官方客户端负载均衡实现 |
| 服务调用 | **OpenFeign** + Spring 6 HTTP Interface | 声明式 + 函数式两种风格 |
| 熔断降级 | **Resilience4j**（代码级）+ **Sentinel**（规则级） | 两者互补，覆盖细粒度与规则级流控 |
| 认证授权 | **Spring Authorization Server** + **Spring Security 6** | 官方 OAuth2 授权服务器框架 |
| 分布式事务 | **Seata 2.1** | AT/TCC/Saga/XA 四种模式 |
| 数据库 | **MySQL 8.4 (LTS)** | 长期支持版 |
| 分库分表 | **ShardingSphere 5.5** | 按需引入 |
| 缓存 | **Redis 7.4 Cluster** | 原生集群，内置高可用+分片 |
| 分布式锁 | **Redisson 3.x** | Redis 分布式锁标准库 |
| 消息队列 | **RocketMQ 5.3** | 原生事务消息、延迟消息，电商场景最优 |
| 搜索引擎 | **OpenSearch 2.x** | Apache 2.0 开源，ES 兼容 |
| 对象存储 | **MinIO** | 开源自建，S3 兼容协议 |
| 任务调度 | **XXL-Job 2.4** | 开源分布式调度平台 |
| 数据同步 | **Canal 1.1** | MySQL binlog → MQ → ES/缓存 |
| 链路追踪 | **Micrometer Tracing + OpenTelemetry + Tempo** | CNCF 可观测性标准 |
| 监控 | **Prometheus + Grafana + VictoriaMetrics** | 指标采集与可视化，长期存储增强 |
| 日志 | **Loki + Promtail** | 轻量，与 Grafana 统一生态 |
| 告警 | **Alertmanager + Grafana Alerting** | 多通道告警路由 |
| 容器化 | **Docker + K8s 1.30 + Helm** | 容器编排标准 |
| CI | **GitHub Actions** | 云原生 CI，YAML 声明式，零运维 |
| CD | **ArgoCD (GitOps)** | 声明式 CD，Git 为唯一事实来源 |
| 镜像仓库 | **Harbor 2.x** | 企业级，带安全扫描 |
| ORM | **MyBatis-Plus 3.5+** | 增强 CRUD，代码生成 |
| API 文档 | **SpringDoc OpenAPI 3** | 自动生成 Swagger UI |
| 前端 | **Vue 3 + TypeScript + Vite** | Composition API，类型安全，极速 HMR |
| UI 组件库 | **Element Plus** | Vue 3 生态最成熟的企业级 UI 库 |
| 前端设计 | **Figma**（免费版） | 云端协作 UI 设计，Dev Mode 标注交付，Auto Layout 对齐组件化思路 |

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                  用户终端（Web / App / 小程序）                        │
│             前端：Vue 3 + TypeScript + Vite                          │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │  CDN + WAF（外网防护） │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Spring Cloud Gateway │
                    │  (统一入口/鉴权/限流)  │
                    └──────────┬──────────┘
                               │
        ┌──────────┬───────────┼───────────┬──────────┐
        │          │           │           │          │
   ┌────▼────┐┌────▼────┐┌────▼────┐┌────▼────┐┌────▼────┐
   │mall-auth││mall-    ││mall-    ││mall-    ││mall-    │
   │认证授权  ││product  ││member   ││order    ││ware     │
   │         ││商品中心  ││会员中心  ││订单中心  ││库存中心  │
   └────┬────┘└────┬────┘└────┬────┘└────┬────┘└────┬────┘
        │          │          │          │          │
        └──────────┴──────────┼──────────┴──────────┘
                              │
                              │ OpenFeign + LoadBalancer
                              │ Resilience4j + Sentinel
                              │ Seata (分布式事务)
                              │
        ┌──────────┬──────────┼──────────┬──────────┐
        │          │          │          │          │
   ┌────▼───┐┌────▼────┐┌────▼────┐┌────▼────┐┌────▼────┐
   │ Nacos  ││ Redis   ││ MySQL   ││RocketMQ ││OpenSearch│
   │ 2.4    ││ 7.4     ││ 8.4     ││ 5.3     ││ 2.x    │
   │注册/配置││Cluster  ││LTS      ││事务/延迟  ││全文检索  │
   └────────┘└─────────┘└─────────┘└─────────┘└─────────┘

   ┌────────────────────────────────────────────────────────────────┐
   │                     可观测性                                     │
   │  Micrometer Tracing + OpenTelemetry → Tempo（链路追踪）          │
   │  Micrometer + Prometheus + VictoriaMetrics（指标监控）           │
   │  Promtail + Loki（日志聚合）                                    │
   │  Grafana（统一可视化大盘）                                       │
   │  Alertmanager + Grafana Alerting（告警）                        │
   └────────────────────────────────────────────────────────────────┘

   ┌────────────────────────────────────────────────────────────────┐
   │                     CI/CD (GitOps)                             │
   │  GitHub Actions → 构建/测试/扫描 → 推送 Harbor                  │
   │  ArgoCD → 检测 Git 变更 → 自动同步 K8s                         │
   │  Helm Charts（应用打包）                                        │
   └────────────────────────────────────────────────────────────────┘
```

### 2.2 流量链路

```
用户请求 → CDN → WAF → Spring Cloud Gateway（鉴权/限流/路由）
  → LoadBalancer（负载均衡）
    → 业务微服务（OpenFeign 调用 / Resilience4j 熔断 / Sentinel 流控）
      → MySQL / Redis / RocketMQ / OpenSearch / MinIO
```

### 2.3 服务治理机制

| 能力 | 实现方式 |
|------|---------|
| 服务注册与发现 | 各服务启动时注册到 Nacos，调用方从 Nacos 获取实例列表 |
| 客户端负载均衡 | Spring Cloud LoadBalancer 从实例列表中选择节点 |
| 服务间调用 | OpenFeign（声明式 HTTP）或 Spring 6 HTTP Interface |
| 熔断降级 | Resilience4j（方法级重试/舱壁/超时）+ Sentinel（接口级 QPS 限流/热点参数限流） |
| 配置管理 | Nacos 配置中心，支持动态推送、环境隔离、版本回滚 |
| 分布式事务 | Seata AT 模式（自动补偿）/ TCC 模式（手动确认） |
| 认证授权 | Spring Authorization Server 颁发 JWT，Spring Security 6 校验 |

---

## 三、业务服务划分

| 服务名 | 职责 | 数据库 |
|--------|------|--------|
| `mall-gateway` | API 网关（统一入口、鉴权、限流、路由） | — |
| `mall-auth` | 认证授权中心（OAuth2 登录、JWT 颁发） | `mall_auth` |
| `mall-member` | 会员中心（注册、登录、收货地址、积分） | `mall_member` |
| `mall-product` | 商品中心（SPU/SKU、分类、品牌、属性） | `mall_product` |
| `mall-search` | 搜索服务（商品检索、聚合筛选，对接 OpenSearch） | — |
| `mall-cart` | 购物车（Redis Hash 存储） | — |
| `mall-order` | 订单中心（下单、状态流转、超时取消） | `mall_order` |
| `mall-ware` | 库存中心（库存扣减、回滚、预警） | `mall_ware` |
| `mall-coupon` | 营销中心（优惠券、满减、促销活动） | `mall_coupon` |
| `mall-seckill` | 秒杀服务（Redis 预减库存 + RocketMQ 削峰） | `mall_seckill` |
| `mall-third` | 第三方服务（短信、支付、OSS 上传） | — |
| `mall-admin` | 后台管理（商品上下架、订单管理、数据看板） | — |

---

## 四、关键选型理由

### ① Java 21 虚拟线程
一行配置启用，以同步写法获得异步性能，Tomcat 并发从 200 线程提升到无上限虚拟线程，是 Java 近 10 年最重要的特性。

### ② Resilience4j + Sentinel 分工
- **Resilience4j**：代码级细粒度控制（单方法的重试、舱壁隔离、超时控制）
- **Sentinel**：规则级流控（接口 QPS 限流、热点参数限流、系统保护）
- 两者互补，不冲突

### ③ RocketMQ 替代 RabbitMQ
电商场景核心需求对比：

| 能力 | RabbitMQ | RocketMQ |
|------|----------|----------|
| 事务消息 | 需自行实现 | **原生支持**（半消息+回查） |
| 延迟消息 | TTL+死信队列（有顺序问题） | **原生多级延迟**（1s~2h） |
| 消息堆积 | 一般（内存受限） | **极强**（磁盘顺序写） |
| 吞吐量 | 万级 TPS | 十万级 TPS |
| 顺序消息 | 需特殊设计 | **原生支持** |

### ④ OpenSearch 替代 Elasticsearch
2021 年 Elastic 将 ES 许可证改为 SSPL（非开源），AWS fork 出 OpenSearch，完全 Apache 2.0 开源，API 高度兼容，Linux 基金会托管。

### ⑤ GitOps 替代 Jenkins
- **GitHub Actions**：云原生 CI，YAML 声明式，与代码仓库深度集成，零运维
- **ArgoCD**：K8s 原生 CD，Git 仓库为唯一事实来源，自动向声明状态收敛，支持渐进式发布

---

## 五、备选方案：Service Mesh（进阶）

在掌握经典微服务后，可引入 **Istio** 体验 Service Mesh：

| 维度 | 经典微服务（首选） | Service Mesh（进阶） |
|------|------------------|---------------------|
| 服务治理 | SDK 嵌入业务代码 | Sidecar 代理，业务零侵入 |
| 流量管理 | Gateway + OpenFeign | Istio VirtualService + DestinationRule |
| 熔断限流 | Resilience4j + Sentinel | Envoy 原生熔断 + Istio 限流 |
| 链路追踪 | OpenTelemetry SDK | Istio 自动注入 Trace Header |
| 服务间加密 | 需自行实现 | Istio 自动 mTLS |
| 金丝雀发布 | Argo Rollouts | Istio 权重路由 + Argo Rollouts |
| 可视化 | Grafana | Grafana + Kiali（服务拓扑图） |

额外引入：Istio、Kiali、APISIX（替代 Gateway 做南北向流量）、Argo Rollouts、Cert-Manager

> **建议**：先用经典微服务跑通完整商城，再在部分服务上引入 Istio，循序渐进。

---

## 六、版本参考

| 技术 | 版本 | 技术 | 版本 |
|------|------|------|------|
| JDK | 21 LTS | Spring Boot | 3.4.x |
| Spring Cloud | 2024.0.x | Spring Cloud Alibaba | 2023.0.x |
| Nacos | 2.4.x | Sentinel | 1.8.6+ |
| Seata | 2.1.x | MySQL | 8.4 LTS |
| Redis | 7.4.x | RocketMQ | 5.3.x |
| OpenSearch | 2.x | ShardingSphere | 5.5.x |
| Kubernetes | 1.30+ | ArgoCD | 2.11+ |
| Istio | 1.22+ | Harbor | 2.x |

---

## 七、一句话总结

**Java 21 + Spring Boot 3.4 + Spring Cloud 2024 + Nacos + Gateway + OpenFeign + LoadBalancer + Resilience4j + Sentinel + Seata + MySQL 8.4 + ShardingSphere + Redis 7 Cluster + RocketMQ 5 + OpenSearch + MinIO + XXL-Job + Canal + OpenTelemetry + Prometheus + Grafana + Loki + K8s + Helm + GitHub Actions + ArgoCD + Harbor**

这套技术栈是 2026 年国内微服务的事实标准，覆盖从开发到运维全链路，每一项都是该领域最流行/最优的选择。
