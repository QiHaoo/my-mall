# 项目记忆 - my-mall 商城系统

## 项目性质
- 学习项目：用户用于从零学习微服务架构开发的电商商城系统
- 参考来源：谷粒商城（2020 年技术栈教程），用户计划用 2026 最新技术栈从零重写

## 技术选型决策（2026 推荐方案一：经典微服务升级版）
- Java 21 (虚拟线程) + Spring Boot 3.4 + Spring Cloud 2024
- Nacos 2.4 (注册+配置) + Spring Cloud Gateway 4.x
- OpenFeign + Spring Cloud LoadBalancer (替代已废弃的 Ribbon)
- Resilience4j (替代已废弃的 Hystrix) + Sentinel
- Spring Authorization Server + Spring Security 6
- Seata 2.1 分布式事务
- MySQL 8.4 + ShardingSphere 5.5 分库分表
- Redis 7.4 Cluster + Redisson
- RocketMQ 5.3 (事务消息+延迟消息，替代 RabbitMQ)
- OpenSearch 2.x (替代 ES，开源许可) + MinIO (替代 OSS)
- Micrometer Tracing + OpenTelemetry + Tempo (替代已废弃的 Sleuth+Zipkin)
- Prometheus + Grafana + Loki (替代 ELK)
- K8s 1.30 + Helm + GitHub Actions + ArgoCD (GitOps，替代 Jenkins)

## 关键技术演进要点（已废弃组件）
- Ribbon → Spring Cloud LoadBalancer
- Hystrix → Resilience4j
- Sleuth → Micrometer Tracing + OpenTelemetry
- Spring Boot 2.x → 3.x (要求 Java 17+)
- 整个 Netflix OSS 全家桶已停止维护

## 文档位置
- legacy-architecture-details.md: 原始架构图翻译（用户提供）
- architecture-analysis-2020-vs-2026.md: 完整分析文档（含两套方案+学习路线）
- docker-compose.yml: 全部中间件编排（profiles 分组：core/mq/search/storage/monitor）
- dev-environment-setup.md: 本地开发环境搭建指南（WSL2+Docker 方案）
- config/prometheus/prometheus.yml: Prometheus 采集配置

## 开发环境方案决策
- 推荐 WSL2 + Docker Engine + Docker Compose（不用 VMware+CentOS 虚拟机）
- 理由：WSL2 层数少、性能接近原生、内存按需回收；CentOS 8 已 EOL
- 中间件用 docker-compose profiles 分阶段启动，避免一次占满内存

## 用户偏好
- 技术选型按「最优/最流行」原则
- 优先基于 AI 自身知识库分析，搜索内容需谨慎评估质量
