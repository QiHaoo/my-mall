# 路由、Predicate 与 Filter

## 1. Predicate（路由断言）

### 1.1 常用内置 Predicate

| Predicate | 说明 | 示例 |
|-----------|------|------|
| `Path` | 路径匹配 | `Path=/api/product/**` |
| `Method` | HTTP 方法 | `Method=GET,POST` |
| `Header` | 请求头匹配 | `Header=X-Request-Id, \d+` |
| `Query` | 查询参数 | `Query=color,red` |
| `Host` | 域名匹配 | `Host=*.mall.com` |
| `After` | 时间之后 | `After=2024-01-01T00:00:00+08:00` |
| `Before` | 时间之前 | `Before=2024-12-31T23:59:59+08:00` |
| `Between` | 时间范围 | `Between=开始时间,结束时间` |
| `Weight` | 权重路由 | `Weight=group1, 80` |
| `RemoteAddr` | 客户端 IP | `RemoteAddr=192.168.1.0/24` |

### 1.2 组合 Predicate

同一路由下多个 Predicate 为 AND 关系（全部满足才匹配）：

```yaml
routes:
  - id: product-get
    uri: lb://mall-product
    predicates:
      - Path=/api/product/**      # 路径匹配
      - Method=GET                # 且为 GET 请求
      - Header=X-Token, .+        # 且带 Token 头
```

不同路由之间为优先级关系（按顺序匹配，先匹配先命中）。

### 1.3 Predicate 实战示例

```yaml
spring:
  cloud:
    gateway:
      routes:
        # 商品服务：/api/product/** → mall-product
        - id: product-route
          uri: lb://mall-product
          predicates:
            - Path=/api/product/**
          filters:
            - StripPrefix=1

        # 订单服务：/api/order/** → mall-order
        - id: order-route
          uri: lb://mall-order
          predicates:
            - Path=/api/order/**
          filters:
            - StripPrefix=1

        # 会员服务：/api/member/** → mall-member
        - id: member-route
          uri: lb://mall-member
          predicates:
            - Path=/api/member/**
          filters:
            - StripPrefix=1

        # 营销服务：/api/coupon/** → mall-coupon
        - id: coupon-route
          uri: lb://mall-coupon
          predicates:
            - Path=/api/coupon/**
          filters:
            - StripPrefix=1
```

## 2. Filter（过滤器）

### 2.1 常用内置 Filter

| Filter | 说明 | 示例 |
|--------|------|------|
| `StripPrefix` | 去掉路径前缀 | `StripPrefix=1`（去 1 级） |
| `PrefixPath` | 添加路径前缀 | `PrefixPath=/api` |
| `AddRequestHeader` | 添加请求头 | `AddRequestHeader=X-Source, gateway` |
| `RemoveRequestHeader` | 移除请求头 | `RemoveRequestHeader=Cookie` |
| `AddResponseHeader` | 添加响应头 | `AddResponseHeader=X-Debug, true` |
| `SetPath` | 重写路径 | `SetPath=/v2{segment}` |
| `RewritePath` | 正则重写路径 | `RewritePath=/old, /new` |
| `RedirectTo` | 重定向 | `RedirectTo=302, /login` |
| `RequestRateLimiter` | 请求限流 | `RequestRateLimiter` + Redis |
| `CircuitBreaker` | 熔断降级 | `CircuitBreaker=myBreaker` |
| `Retry` | 重试 | `Retry=3` |
| `SetStatus` | 设置状态码 | `SetStatus=200` |

### 2.2 StripPrefix 详解

```yaml
# 配置
- Path=/api/product/**
- StripPrefix=1

# 效果
请求: /api/product/list
转发: /product/list  （去掉了 /api）

# StripPrefix=2 效果
请求: /api/product/list
转发: /list  （去掉了 /api/product）
```

### 2.3 RewritePath 详解

```yaml
# 将 /product/{id} 重写为 /api/v2/product/{id}
- RewritePath=/product/(?<segment>.*), /api/v2/product/${segment}

# 请求: /product/123
# 转发: /api/v2/product/123
```

### 2.4 Filter 执行顺序

```
请求进入
    │
    ▼
GatewayFilter Pre 阶段（按 order 从小到大执行）
    │
    ▼
转发到下游
    │
    ▼
GatewayFilter Post 阶段（按 order 从大到小执行）
    │
    ▼
返回响应
```

通过 `@Order` 或 `Ordered` 接口控制顺序：
- **order 越小，Pre 阶段越先执行**
- **Post 阶段是 Pre 的逆序**

## 3. 自定义 GatewayFilter

### 3.1 请求日志 Filter

```java
@Component
public class RequestLogFilter implements GatewayFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        log.info(">>> Gateway Request: {} {}", method, path);

        long startTime = System.currentTimeMillis();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;
            log.info("<<< Gateway Response: {} {} -> {} ({}ms)", method, path, statusCode, duration);
        }));
    }

    @Override
    public int getOrder() {
        return -100;  // 高优先级
    }
}
```

### 3.2 在路由中使用自定义 Filter

```java
@Bean
public RouteLocator customRoutes(RouteLocatorBuilder builder, RequestLogFilter logFilter) {
    return builder.routes()
            .route("product-route", r -> r
                    .path("/api/product/**")
                    .filters(f -> f
                            .filter(logFilter)        // 添加自定义 Filter
                            .stripPrefix(1))
                    .uri("lb://mall-product"))
            .build();
}
```

## 4. 自定义 GlobalFilter

GlobalFilter 作用于所有路由，无需在配置中声明。

### 4.1 统一鉴权 Filter

```java
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    // 不需要鉴权的路径
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/product/list"    // 商品列表无需登录
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单放行
        if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // 检查 Token
        String token = request.getHeaders().getFirst("Authorization");
        if (token == null || token.isBlank()) {
            log.warn("鉴权失败：缺少 Authorization 头, path={}", path);
            return unauthorized(exchange, "未登录，请先登录");
        }

        // TODO: 验证 JWT Token，解析用户信息
        // 将用户 ID 传递给下游服务
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", "12345")
                .header("X-User-Name", "testUser")
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"code\":401,\"message\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -200;  // 比日志 Filter 更先执行
    }
}
```

### 4.2 Filter 优先级参考

| Filter | Order | 说明 |
|--------|-------|------|
| AuthGlobalFilter | -200 | 鉴权（最先执行） |
| RequestLogFilter | -100 | 日志（鉴权后） |
| NettyRoutingFilter | 2147483647 | 框架内置（转发请求，最后执行） |

## 5. 跨域配置（CORS）

### 5.1 全局 CORS 配置

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "http://localhost:5173"   # Vite 开发服务器
            allowed-methods: "*"
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600
```

### 5.2 代码配置（更灵活）

```java
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
```

> **注意**：跨域只在网关配置，下游微服务不需要再配置，否则会重复。

## 6. 默认路由与 404 处理

```yaml
spring:
  cloud:
    gateway:
      routes:
        # ... 正常路由

        # 兜底路由：未匹配的请求返回友好提示
        - id: not-found
          uri: no://op                  # 特殊 URI，不转发
          predicates:
            - Path=/**
          order: 9999                   # 最低优先级
          filters:
            - SetStatus=404
```

## 7. 下一步

- [生产实践](./04-production-practices.md)
- [项目实施记录](./project-implementation.md)
