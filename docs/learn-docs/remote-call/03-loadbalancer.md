# 负载均衡

## 1. 为什么需要负载均衡

一个服务通常部署多个实例，需要从多个实例中选一个来调用：

```
mall-coupon 实例1 (192.168.1.10:7001)
mall-coupon 实例2 (192.168.1.11:7001)
mall-coupon 实例3 (192.168.1.12:7001)

mall-member 调用 mall-coupon → 选哪个实例？
```

## 2. 两种负载均衡

| | 服务端负载均衡 | 客户端负载均衡 |
|------|------|------|
| 谁做 | Nginx / Gateway | 调用方自己 |
| 中间节点 | 有（LB 转发） | 无（直连） |
| 单点风险 | LB 可能成为瓶颈 | 无 |
| 微服务推荐 | ❌ | ✅ |

> 微服务推荐**客户端负载均衡**，没有中间转发节点，更灵活。

## 3. Spring Cloud LoadBalancer

Ribbon 已停止维护，Spring Cloud 官方替代品是 **Spring Cloud LoadBalancer**。

### 与 Nacos 的协作

```
1. Feign 收到调用 mall-coupon
2. LoadBalancer 从 Nacos 获取 mall-coupon 的所有实例信息
3. 根据策略选一个实例
4. Feign 向该实例发起 HTTP 请求
```

### 无需额外配置

引入 `spring-cloud-starter-loadbalancer` 后，Feign 默认就通过 LoadBalancer 解析服务名，不需要写任何配置。

### 默认策略

**轮询（Round Robin）**，按顺序轮流选择实例。

> 如需自定义策略（如权重、最少连接），可通过 `@Bean` 定义 `ReactorServiceInstanceLoadBalancer`，但一般项目使用默认轮询即可。

## 4. 关键决策：Ribbon 为什么退役

- Netflix 在 2018 年宣布 Ribbon 进入维护模式
- Spring Cloud 2020.0 开始废弃 Ribbon
- 2024/2025 版本中 Ribbon 已被完全移除

**迁移对照：**

| Ribbon | Spring Cloud LoadBalancer |
|------|------|
| `@RibbonClient` | `@LoadBalancerClient` |
| `IRule` | `ReactorServiceInstanceLoadBalancer` |
| 阻塞式 | 响应式（支持 WebFlux） |
