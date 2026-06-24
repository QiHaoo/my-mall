# 最佳实践

## 1. 超时配置

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:                          # 全局默认
            connect-timeout: 3000           # 3s 连接超时
            read-timeout: 5000              # 5s 读取超时
          mall-coupon:                      # 针对特定服务的覆盖配置
            read-timeout: 10000             # coupon 接口可能较慢，加长时间
```

## 2. 日志配置

```yaml
logging:
  level:
    com.mymall.member.feign.CouponFeignClient: DEBUG  # 打印 Feign 请求详情
```

Feign 日志级别：

| 级别 | 输出内容 |
|------|------|
| NONE | 不打印（生产默认） |
| BASIC | 请求方法 + URL + 响应状态码 |
| HEADERS | BASIC + 请求/响应头 |
| FULL | HEADERS + 请求/响应体（**开发期用，生产禁用**） |

## 3. 超时重试

**不要无脑重试。** 重试适用于幂等操作（GET），不适用于非幂等操作（POST 扣款）。

```java
// 示例：只对 GET 做重试，最多 2 次，间隔 500ms
@Bean
public Retryer feignRetryer() {
    return new Retryer.Default(500, 500, 2);
}
```

> 一般推荐结合 Resilience4j / Sentinel 的 `@Retry` 注解，比 Feign 内置 Retryer 更可控。

## 4. 熔断降级

Feign 本身不做熔断，需要配合：

| 框架 | 用法 |
|------|------|
| Sentinel | `@FeignClient(fallback = XxxFallback.class)` |
| Resilience4j | `@CircuitBreaker(name = "mall-coupon")` |

```java
// Sentinel 降级示例
@FeignClient(name = "mall-coupon", path = "/coupon/coupon",
             fallback = CouponFeignClientFallback.class)
public interface CouponFeignClient {
    R list();
}

// 降级类：当 coupon 服务不可用时返回兜底数据
@Component
class CouponFeignClientFallback implements CouponFeignClient {
    @Override
    public R list() {
        return R.error("优惠券服务暂不可用，请稍后重试");
    }
}
```

## 5. 测试策略

| 层级 | 方式 | 验证范围 |
|------|------|------|
| 单元测试 | `@WebMvcTest` + `@MockitoBean` mock FeignClient | Controller 逻辑 |
| 集成测试 | `@SpringBootTest` + WireMock | Feign 序列化/HTTP/反序列化 |
| E2E | Testcontainers 启动真实服务 | 全链路 |

> 集成测试用 WireMock 在 HTTP 层拦截，是 Feign 测试的最佳实践。详见 [E2E 测试方案对比](../testing/e2e-testing-strategies.md)。

## 6. 不要在一个服务里定义另一个服务的 DTO

```java
// ❌ 错误：在 member 里复制了 coupon 的 Coupon 实体字段
public class CouponDTO {  // 这属于 coupon 模块
    private String couponName;
    private BigDecimal amount;
}

// ✅ 正确：Feign 返回值直接用共同依赖的 R+Map 或共享 DTO 模块
// 或返回 R 后用 Map 获取数据。
```

## 7. 避坑清单

- **3.5.4+ 的 `mybatis-plus-jsqlparser` 和 Feign 无关**，但容易被一起查到 —— 那是 MyBatis 分页插件的依赖
- **Feign 请求默认不传 Header**（Cookie / Authorization），需要自己写拦截器透传
- **不要在一个 Controller 内链式 Feign 调用多个服务**—— 耦合度高，考虑用 Service 编排
- **测试用 `spring.cloud.openfeign.client.config.{name}.url`** 可以直接绕过 Nacos，不需要改代码
