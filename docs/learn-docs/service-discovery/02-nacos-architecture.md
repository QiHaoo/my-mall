# Nacos 架构与核心特性

## 1. Nacos 架构概览

```
                    ┌─────────────────────────────────────────┐
                    │              Nacos Server                │
                    │  ┌─────────────────────────────────┐    │
                    │  │         Naming Service           │    │
                    │  │  ┌─────────┐  ┌─────────────┐   │    │
                    │  │  │ Service │  │   Health     │   │    │
                    │  │  │  Store  │  │   Check      │   │    │
                    │  │  └─────────┘  └─────────────┘   │    │
                    │  └─────────────────────────────────┘    │
                    │  ┌─────────────────────────────────┐    │
                    │  │         Config Service           │    │
                    │  │  ┌─────────┐  ┌─────────────┐   │    │
                    │  │  │ Config  │  │   Config     │   │    │
                    │  │  │  Store  │  │   Push       │   │    │
                    │  │  └─────────┘  └─────────────┘   │    │
                    │  └─────────────────────────────────┘    │
                    │  ┌─────────────────────────────────┐    │
                    │  │           Consistency             │    │
                    │  │      (Distro / Raft / JRaft)      │    │
                    │  └─────────────────────────────────┘    │
                    └─────────────────────────────────────────┘
```

## 2. 核心组件

### 2.1 Naming Service（服务管理）

负责服务实例的注册、发现、健康检查。

| 功能 | 说明 |
|------|------|
| 服务注册 | 接收 Provider 注册请求，存储实例信息 |
| 服务发现 | Consumer 拉取/订阅实例列表 |
| 健康检查 | 心跳检测 + 主动探测 |
| 服务路由 | 权重、元数据匹配 |

### 2.2 Config Service（配置管理）

动态配置管理，支持配置热更新。

| 功能 | 说明 |
|------|------|
| 配置存储 | 持久化到 MySQL / Derby |
| 配置推送 | 长轮询 + 推送 |
| 版本管理 | 配置历史、回滚 |
| 灰度发布 | 按 IP、标签灰度 |

### 2.3 Consistency（一致性协议）

| 协议 | 适用场景 | 特点 |
|------|---------|------|
| **Distro** | 临时实例（AP） | 最终一致性，高性能 |
| **Raft** | 持久实例（CP） | 强一致性，数据可靠 |
| **JRaft** | 集群模式 | Raft 的 Java 实现 |

## 3. 数据模型

```
Namespace
    └── Group
        └── Service
            └── Cluster
                └── Instance
```

### 3.1 Namespace

```yaml
# 物理隔离，不同 Namespace 的服务互不可见
spring:
  cloud:
    nacos:
      discovery:
        namespace: my-mall  # 或 UUID
```

### 3.2 Group

```yaml
# 逻辑分组，同一 Group 下的服务才能互相发现
spring:
  cloud:
    nacos:
      discovery:
        group: DEFAULT_GROUP
```

### 3.3 Service

```yaml
# 服务名称，全局唯一标识
spring:
  application:
    name: mall-product
```

### 3.4 Cluster

```yaml
# 集群名称，用于就近访问
spring:
  cloud:
    nacos:
      discovery:
        cluster-name: hangzhou
```

## 4. 服务注册流程

```
┌──────────┐                              ┌──────────────┐
│  Provider │                              │ Nacos Server │
└────┬─────┘                              └──────┬───────┘
     │                                           │
     │  1. Register (PUT /nacos/v1/ns/instance)  │
     │─────────────────────────────────────────► │
     │                                           │
     │  2. Response (OK)                         │
     │◄───────────────────────────────────────── │
     │                                           │
     │  3. Heartbeat (every 5s)                  │
     │─────────────────────────────────────────► │
     │                                           │
     │  4. Response (OK)                         │
     │◄───────────────────────────────────────── │
     │                                           │
```

## 5. 服务发现流程

```
┌──────────┐                              ┌──────────────┐
│ Consumer │                              │ Nacos Server │
└────┬─────┘                              └──────┬───────┘
     │                                           │
     │  1. Pull (GET /nacos/v1/ns/instance/list) │
     │─────────────────────────────────────────► │
     │                                           │
     │  2. Response (Instance List)              │
     │◄───────────────────────────────────────── │
     │                                           │
     │  3. Subscribe (UDP Port)                  │
     │─────────────────────────────────────────► │
     │                                           │
     │  4. Push (UDP Packet on change)           │
     │◄───────────────────────────────────────── │
     │                                           │
```

## 6. 健康检查机制

### 6.1 临时实例（心跳模式）

```
Provider                         Nacos
   │                               │
   │──── Heartbeat (5s) ──────────►│
   │                               │
   │──── Heartbeat (10s) ─────────►│  (超过 15s 未心跳)
   │                               │──► Mark Unhealthy
   │                               │
   │──── Heartbeat (20s) ─────────►│
   │                               │──► Mark Healthy
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 心跳间隔 | 5s | Provider 发送心跳 |
| 不健康阈值 | 15s | 超过标记为不健康 |
| 剔除阈值 | 30s | 超过从列表移除 |

### 6.2 持久实例（主动探测）

Nacos Server 主动发起 TCP/HTTP 探测：

```yaml
# 健康检查配置
spring:
  cloud:
    nacos:
      discovery:
        ephemeral: false  # 持久实例
```

## 7. 负载均衡

### 7.1 Spring Cloud LoadBalancer

```java
// 自动负载均衡调用
@LoadBalanced
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}

// 调用：服务名替代 IP
restTemplate.getForObject("http://mall-product/product/1", String.class);
```

### 7.2 权重路由

在 Nacos 控制台设置实例权重（0-100）：

```
Instance A: weight=80  → 接收 80% 流量
Instance B: weight=20  → 接收 20% 流量
```

## 8. 元数据（Metadata）

```yaml
spring:
  cloud:
    nacos:
      discovery:
        metadata:
          version: v1.0
          env: prod
          region: hangzhou
```

用途：
- **灰度发布**：按 version 路由
- **就近访问**：按 region 路由
- **标签过滤**：按 env 过滤

## 9. 集群部署

```
           ┌─────────────┐
           │   Nginx     │
           │  (VIP/SLB)  │
           └──────┬──────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
┌───┴───┐    ┌────┴────┐   ┌───┴───┐
│Nacos-1│◄──►│ Nacos-2 │◄─►│Nacos-3│
└───┬───┘    └────┬────┘   └───┬───┘
    │             │             │
    └─────────────┼─────────────┘
                  │
            ┌─────┴─────┐
            │   MySQL   │
            └───────────┘
```

### 集群配置

```properties
# application.properties
nacos.member-list=192.168.1.1:8848,192.168.1.2:8848,192.168.1.3:8848

# 数据源配置
spring.datasource.platform=mysql
db.num=1
db.url.0=jdbc:mysql://localhost:3306/nacos_config?...
```

## 10. 下一步

- [Spring Cloud Alibaba Nacos Discovery 实战](./03-spring-cloud-alibaba-nacos.md)
- [最佳实践与常见问题](./04-best-practices.md)
