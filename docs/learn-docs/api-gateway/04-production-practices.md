# 生产实践

## 1. Sentinel 网关限流

### 1.1 依赖配置

```xml
<!-- 已在 mall-gateway/pom.xml 中配置 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
</dependency>
```

### 1.2 限流维度

Sentinel Gateway 支持三种限流维度：

| 维度 | 说明 | 适用场景 |
|------|------|---------|
| **路由级** | 按路由 ID 限流 | 保护整个服务 |
| **API 分组** | 按自定义 API 组限流 | 保护某类接口 |
| **参数级** | 按请求参数限流 | 按用户/IP 限流 |

### 1.3 限流配置示例

```java
@Component
public class GatewayConfig {

    @PostConstruct
    public void initGatewayRules() {
        // 路由级限流：product-route 每秒最多 100 次
        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(new GatewayFlowRule("product-route")
                .setCount(100)
                .setIntervalSec(1));

        // 路由级限流：order-route 每秒最多 50 次
        rules.add(new GatewayFlowRule("order-route")
                .setCount(50)
                .setIntervalSec(1));

        GatewayRuleManager.loadRules(rules);
    }
}
```

### 1.4 自定义限流响应

```java
@Component
public class SentinelGatewayConfig {

    @PostConstruct
    public void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler((exchange, throwable) -> {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            String body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\"}";
            DataBuffer buffer = response.bufferFactory()
                    .wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        });
    }
}
```

## 2. JWT 统一鉴权

### 2.1 鉴权流程

```
客户端请求 (带 Token)
    │
    ▼
┌─────────────────┐
│ AuthGlobalFilter │
│                  │
│ 1. 白名单放行    │
│ 2. 校验 JWT      │
│ 3. 解析用户信息   │
│ 4. 传递给下游     │
└────────┬────────┘
         │
         ▼
    下游微服务 (通过 Header 获取用户信息)
```

### 2.2 用户信息传递

网关解析 JWT 后，将用户信息放入 Header 传给下游：

```java
// 网关侧
ServerHttpRequest mutatedRequest = request.mutate()
        .header("X-User-Id", userId)
        .header("X-User-Name", userName)
        .build();

return chain.filter(exchange.mutate().request(mutatedRequest).build());
```

```java
// 下游微服务侧
@RestController
public class OrderController {
    @GetMapping("/my-orders")
    public List<Order> myOrders(@RequestHeader("X-User-Id") Long userId) {
        return orderService.findByUserId(userId);
    }
}
```

### 2.3 生产安全要点

- 网关必须校验 Token 签名，防止伪造
- 下游服务不要重复校验 Token（信任网关传递的 Header）
- Token 过期时间合理设置（Access Token 短 + Refresh Token 长）
- 敏感接口（支付、修改密码）增加二次验证

## 3. 灰度发布

### 3.1 按 Header 灰度

```yaml
routes:
  # 灰度路由：带 gray=true Header 的请求走新版本
  - id: product-gray
    uri: lb://mall-product-v2
    predicates:
      - Path=/api/product/**
      - Header=gray, true
    filters:
      - StripPrefix=1
    order: 1

  # 正常路由：其他请求走旧版本
  - id: product-normal
    uri: lb://mall-product
    predicates:
      - Path=/api/product/**
    filters:
      - StripPrefix=1
    order: 2
```

### 3.2 按权重灰度

```yaml
routes:
  # 80% 流量走旧版本
  - id: product-old
    uri: lb://mall-product
    predicates:
      - Path=/api/product/**
      - Weight=group1, 80
    order: 1

  # 20% 流量走新版本
  - id: product-new
    uri: lb://mall-product-v2
    predicates:
      - Path=/api/product/**
      - Weight=group1, 20
    order: 1
```

### 3.3 按 IP 灰度（内网测试）

```java
@Component
public class GrayFilter implements GlobalFilter, Ordered {

    // 灰度 IP 列表
    private static final Set<String> GRAY_IPS = Set.of(
            "192.168.1.10", "192.168.1.11"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = getClientIp(exchange);

        if (GRAY_IPS.contains(clientIp)) {
            // 灰度 IP：路由到新版本
            exchange.getAttributes().put("gray", "true");
        }

        return chain.filter(exchange);
    }
}
```

## 4. 监控与可观测性

### 4.1 Actuator 端点

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,gateway,metrics
  endpoint:
    gateway:
      enabled: true
```

关键端点：

| 端点 | 说明 |
|------|------|
| `/actuator/gateway/routes` | 查看当前所有路由 |
| `/actuator/gateway/routefilters` | 查看所有 Filter |
| `/actuator/health` | 健康检查 |
| `/actuator/metrics` | 指标数据 |

### 4.2 链路追踪

网关作为请求入口，生成 TraceId 并传递给下游：

```java
@Component
public class TraceFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = UUID.randomUUID().toString().replace("-", "");

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-Trace-Id", traceId)
                .build();

        exchange.getResponse().getHeaders().add("X-Trace-Id", traceId);

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -300;  // 最早执行
    }
}
```

### 4.3 日志规范

```yaml
logging:
  level:
    org.springframework.cloud.gateway: info
    com.mymall.gateway: debug
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

## 5. 高可用部署

### 5.1 网关集群架构

```
                    ┌──────────────────┐
                    │     Nginx/LB     │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Gateway 1 │  │ Gateway 2 │  │ Gateway 3 │
        │ (实例)    │  │ (实例)    │  │ (实例)    │
        └──────────┘  └──────────┘  └──────────┘
              │              │              │
              └──────────────┼──────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Product  │  │  Order   │  │  Member  │
        └──────────┘  └──────────┘  └──────────┘
```

### 5.2 部署要点

| 要点 | 说明 |
|------|------|
| **无状态** | 网关不存储会话，Token 鉴权无状态 |
| **多实例** | 至少 2 个网关实例，前置 Nginx/LB 做负载均衡 |
| **优雅停机** | 配置 `server.shutdown=graceful`，等待请求处理完 |
| **健康检查** | `/actuator/health` 配合 K8s Liveness/Readiness |
| **配置热更新** | 路由配置从 Nacos 拉取，变更实时生效 |

### 5.3 生产配置检查清单

- [ ] 网关注册到 Nacos，服务名 `mall-gateway`
- [ ] 路由配置从 Nacos 配置中心加载
- [ ] Sentinel 限流规则已配置
- [ ] JWT 鉴权 Filter 已启用
- [ ] CORS 只允许生产域名
- [ ] 白名单已审核（只放必要的公开接口）
- [ ] 日志级别为 `info`
- [ ] 至少 2 个实例部署
- [ ] Actuator 端点已配置鉴权（或只暴露内网）

## 6. 常见问题

### Q1: 网关无法路由到下游服务？

**检查清单**：
- 下游服务是否注册到 Nacos
- `uri: lb://服务名` 中的服务名是否正确（区分大小写）
- Spring Cloud LoadBalancer 依赖是否引入
- Nacos namespace 是否一致

### Q2: 跨域不生效？

- 网关和下游服务不要同时配置 CORS
- 检查 `allowed-origins` 是否包含前端实际地址
- 带 Cookie 时 `allow-credentials: true` + `allowed-origins` 不能为 `*`

### Q3: Gateway 和 Spring MVC 冲突？

- 网关模块不能引入 `spring-boot-starter-web`
- 检查依赖树中是否有 Servlet 相关依赖

## 7. 下一步

- [项目实施记录](./project-implementation.md)
