# Feign 核心机制

## 1. 动态代理

Feign 的核心是 **JDK 动态代理**。你声明的 `@FeignClient` 接口在运行时被 Feign 生成一个代理对象：

```
你的代码: couponFeignClient.list()
    ↓
代理对象拦截
    ↓
解析 @FeignClient → 获取服务名 + 路径
解析 @GetMapping → 获取 HTTP 方法 + 路径
拼接完整 URL: http://{实例地址}/coupon/coupon/list
    ↓
发起 HTTP GET 请求
    ↓
反序列化 Response → R 对象
    ↓
返回给你的代码
```

## 2. 关键组件

| 组件 | 作用 |
|------|------|
| `Contract` | 解析接口注解（@GetMapping 等）→ 方法元数据 |
| `Encoder` | 请求参数序列化（对象 → JSON / Form） |
| `Decoder` | 响应体反序列化（JSON → 对象） |
| `Client` | 实际发起 HTTP 请求（底层用 `HttpURLConnection` 或 `OkHttp` 或 `Apache HttpClient`） |
| `RequestInterceptor` | 请求拦截器，统一添加 Header（如认证 Token） |
| `Retryer` | 重试策略 |

## 3. 与 Spring MVC 注解的兼容

Feign 原生有自己的一套注解（`@RequestLine` / `@Param`），但 Spring Cloud OpenFeign 通过 **Spring Contract** 让它支持 Spring MVC 注解：

```java
// 这些注解 Feign 都认识，不需要额外学习
@GetMapping
@PostMapping
@PutMapping
@DeleteMapping
@PathVariable
@RequestParam
@RequestBody
```

> 原理：`SpringMvcContract` 替代了 Feign 默认的 `Contract`，解析 Spring MVC 注解。

## 4. 编解码

### 请求编码

```java
// 你的代码
couponFeignClient.list();
// Feign 内部:
// 1. Encoder 将方法参数（如果有）转为 JSON
// 2. 设置 Content-Type: application/json
// 3. 发起 HTTP 请求
```

### 响应解码

```java
// 你的代码声明返回 R
public interface CouponFeignClient {
    R list();  // ← Feign 知道要反序列化为 R 类型
}
// Feign 内部:
// 1. 收到 HTTP 响应 {"code":200,"msg":"success","data":{...}}
// 2. Decoder 将 JSON 反序列化为 R 对象
// 3. 返回给你的代码
```

默认使用 Spring Boot 的 `HttpMessageConverters`（即 Jackson）进行编解码，和 MVC Controller 完全一致。

## 5. 上下文传递

默认情况下，Feign 请求会创建**全新的 HTTP 上下文**，不会自动传递原请求的 Header（如认证 Token）。需要手动配置拦截器：

```java
@Component
public class FeignAuthInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        // 从当前请求上下文获取 Token，添加到 Feign 请求头
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String token = attributes.getRequest().getHeader("Authorization");
            if (token != null) {
                template.header("Authorization", token);
            }
        }
    }
}
```
