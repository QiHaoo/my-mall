# 统一响应与异常体系

> 涉及组件：`R` → `ResultCode` → `BizException` → `GlobalExceptionHandler`
>
> 这四个组件构成一条完整的链路：Controller 返回 `R`，Service 抛 `BizException`，`GlobalExceptionHandler` 统一捕获后转成 `R`，错误码由 `ResultCode` 统一管理。

## 为什么需要统一响应

### 没有统一响应时的问题

```java
// Controller A 直接返回对象
@GetMapping("/user/{id}")
public User getUser(@PathVariable Long id) {
    return userService.getById(id);
}

// Controller B 返回 Map
@GetMapping("/order/{id}")
public Map<String, Object> getOrder(@PathVariable Long id) {
    Map<String, Object> map = new HashMap<>();
    map.put("data", orderService.getById(id));
    map.put("success", true);
    return map;
}

// Controller C 出错了直接抛
@GetMapping("/coupon/{id}")
public Coupon getCoupon(@PathVariable Long id) {
    throw new RuntimeException("优惠券不存在");
    // 前端收到：HTTP 500 + Spring 默认错误 JSON，格式完全不统一
}
```

前端需要为每个接口写不同的解析逻辑，错误处理更是一团乱麻。

### 统一响应体的业界方案

| 方案 | 代表 | 特点 |
|------|------|------|
| HTTP 状态码 + 空 body | RESTful 原教旨主义 | 204 No Content，错误用 4xx/5xx，前端靠状态码判断 |
| HTTP 200 + 业务码 | 国内主流（淘宝、微信、美团） | 所有响应 HTTP 200，body 里有 `code` + `msg` + `data` |
| HTTP 状态码 + 业务码 | GitHub API | 4xx/5xx 的 body 里也有 `code` + `message` |

**本项目选择 HTTP 200 + 业务码**，原因在 `GlobalExceptionHandler` 的注释里写得很清楚：

> 电商系统业务错误种类多，4xx/5xx 会被网关/CDN 拦截导致前端拿不到响应体；统一 200 + 业务码是国内主流电商的通行做法。

实际场景：用户领优惠券已领完，这是业务规则不是服务器错误，如果返回 HTTP 429（Too Many Requests），CDN 可能直接缓存这个错误响应，导致后续用户也领不了。

## R：统一响应体

### 设计要点

```java
@Data
public class R<T> implements Serializable {
    private Integer code;  // 业务状态码：200=成功，其他=失败
    private String msg;    // 提示消息
    private T data;        // 业务数据
}
```

三个字段的设计逻辑：

- **`code`**：独立于 HTTP 状态码，业务自定义。200 表示成功是约定俗成
- **`msg`**：给用户看的提示，不是给开发者看的堆栈
- **`data`**：泛型，灵活携带任意类型数据

### 链式 put 的设计

```java
// 返回多个数据段
R.ok().put("coupons", list).put("total", 2);
```

这个设计的取舍：

- **优点**：一个接口需要返回多个不相干数据段时，不用专门建 VO 类
- **缺点**：`put` 方法里有 `instanceof` 检查和强制转型，类型安全性弱
- **适用场景**：接口聚合查询（如首页需要 banner + 推荐商品 + 公告），数据段之间无结构关联

### 业界对比

淘宝的 `Result` 和微信的 `BaseResponse` 结构几乎一致。但有些框架（如 Spring HATEOAS）会在响应里加 `_links` 字段做超媒体导航，本项目的 `R` 不涉及 HATEOAS，因为是内部管理后台 + 前端分离架构，不需要超媒体。

## ResultCode：错误码枚举

### 码段规划

```java
200        — 成功
400~499    — 通用客户端错误（参数/认证/权限/路由）
500~599    — 通用服务端错误
40001+     — 优惠券服务
50001+     — 商品服务
51001+     — 商品分类（商品服务的子域）
52001+     — 对象存储
60001+     — 订单服务
```

### 为什么用枚举而不是常量类

```java
// 方式一：常量类（有的项目这么干）
public class ResultCodes {
    public static final int SUCCESS = 200;
    public static final int PARAM_ERROR = 400;
    // ...
}

// 方式二：枚举（本项目）
public enum ResultCode {
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    // ...
}
```

枚举的优势：
1. **code 和 message 绑定**：不会出现"code 用了 40001 但 message 忘改"的问题
2. **穷举可见**：IDE 里 `ResultCode.` 一打，所有错误码一目了然，方便查找
3. **不可变**：枚举值天生 final，不会被运行时篡改
4. **可以加字段**：如果以后需要 HTTP 状态码映射、告警级别等，直接给枚举加字段

### 码段分配策略

**按服务分配万级码段**（40001~49999 给优惠券，50001~59999 给商品），而不是连续编号。

为什么不用连续编号（1, 2, 3...）？
- 微服务架构下，各服务独立开发，连续编号必然冲突
- 万级码段给每个服务足够的空间，子域（如商品分类 51001+）还能再分

业界对比：支付宝用 10 位错误码（`ACQ.TRADE_NOT_EXIST`），微信支付用 6 位数字，思路都是"按服务/模块分段"。

## BizException：业务异常

### 为什么不用 checked exception

```java
// 方式一：checked exception（强制 try-catch 或 throws）
public Coupon getCoupon(Long id) throws CouponNotFoundException {
    // 调用方必须 try-catch 或继续 throws，代码冗余
}

// 方式二：RuntimeException（本项目）
public Coupon getCoupon(Long id) {
    Coupon coupon = couponMapper.selectById(id);
    if (coupon == null) {
        throw new BizException(ResultCode.COUPON_NOT_FOUND);
    }
    return coupon;
}
```

`BizException extends RuntimeException`，原因：
1. 业务异常是"可预期的流程分支"，不是"必须强制处理的错误"
2. checked exception 会导致每层方法签名都要 `throws`，污染接口
3. Spring 的 `@Transactional` 默认只对 `RuntimeException` 回滚，用 unchecked 省去 `rollbackFor` 配置

### 三个构造函数的适用场景

```java
// 1. 标准错误码，用枚举的默认 message
throw new BizException(ResultCode.COUPON_NOT_FOUND);
// → "优惠券不存在"

// 2. 自定义 code + message（枚举里没有的场景）
throw new BizException(40010, "该优惠券已被领完");

// 3. 枚举 code + 自定义 message（需要携带上下文信息）
throw new BizException(ResultCode.STOCK_NOT_ENOUGH, "SKU[" + skuId + "] 库存不足");
// → code=80001, message="SKU[123] 库存不足"
```

第三个构造函数最常用——错误码走枚举保证唯一性，message 拼上下文方便排查。

### 业界对比

| 方案 | 代表 | 特点 |
|------|------|------|
| 自定义 BizException | 本项目、大部分国内项目 | 简单直接，code + message |
| Spring 的ResponseStatusException | Spring 官方 | 直接绑定 HTTP 状态码，适合纯 RESTful |
| Assert 断言式 | Hibernate Validator 风格 | `Assert.notNull(obj, "对象不能为空")`，更简洁但不灵活 |

本项目选择自定义 `BizException`，因为需要携带业务码（`ResultCode`），而 `ResponseStatusException` 只支持 HTTP 状态码。

## GlobalExceptionHandler：全局异常处理器

### 处理链路

```
请求进入 → Controller → Service 抛异常
                              ↓
                    GlobalExceptionHandler 捕获
                              ↓
                    转成 R<Void> 返回前端
```

### 四层处理优先级

```java
@RestControllerAdvice(basePackages = "com.mymall")
public class GlobalExceptionHandler {

    // 1. 业务异常 — code 由业务定义
    @ExceptionHandler(BizException.class)

    // 2. 参数校验异常 — code = 400
    @ExceptionHandler(MethodArgumentNotValidException.class)  // @RequestBody 校验
    @ExceptionHandler(BindException.class)                     // 表单参数校验
    @ExceptionHandler(ConstraintViolationException.class)     // @PathVariable/@RequestParam 校验
    @ExceptionHandler(MissingServletRequestParameterException.class)  // 缺少必填参数
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)      // 参数类型不匹配
    @ExceptionHandler(HttpMessageNotReadableException.class)         // JSON 解析失败
    @ExceptionHandler(MaxUploadSizeExceededException.class)           // 上传文件过大

    // 3. Spring MVC 路由异常 — code = 404/405
    @ExceptionHandler(NoResourceFoundException.class)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)

    // 4. 兜底异常 — code = 500
    @ExceptionHandler(Exception.class)
}
```

从具体到通用的顺序很重要——Spring 会选择最匹配的 handler，如果兜底的 `Exception` 放在前面，所有异常都会被它拦截。

### 为什么校验异常要拆这么细

Spring 的参数校验异常类型很多，每种携带的错误信息格式不同：

| 异常 | 触发场景 | 错误信息提取方式 |
|------|---------|----------------|
| `MethodArgumentNotValidException` | `@RequestBody` + `@Valid` 校验失败 | `e.getBindingResult().getFieldErrors()` |
| `BindException` | `@ModelAttribute` 表单参数校验失败 | `e.getFieldErrors()` |
| `ConstraintViolationException` | `@PathVariable`/`@RequestParam` 上的 `@Validated` | `e.getConstraintViolations()` |

如果不分别处理，前端拿到的错误消息可能是 `e.getMessage()`，里面是一大串 Java 内部信息，用户完全看不懂。

### 兜底异常的安全设计

```java
@ExceptionHandler(Exception.class)
public R<Void> handleException(Exception e) {
    log.error("未处理异常", e);  // 服务端记录完整堆栈
    return R.error(ResultCode.INTERNAL_ERROR.getCode(),
                   ResultCode.INTERNAL_ERROR.getMessage());
    // 返回给前端的只是 "服务器内部错误"，不泄露堆栈
}
```

不向前端泄露堆栈是安全原则——堆栈信息可能暴露技术栈版本、类路径、SQL 语句等，攻击者可据此寻找漏洞。

### `@RestControllerAdvice` 的 basePackages

```java
@RestControllerAdvice(basePackages = "com.mymall")
```

限定在 `com.mymall` 包下，不会拦截其他包（如 Spring Boot 自身的 actuator 端点）。

### 业界对比

| 方案 | 特点 |
|------|------|
| `@ControllerAdvice` + `@ExceptionHandler`（本项目） | Spring 官方推荐，声明式，简洁 |
| HandlerInterceptor 手动 try-catch | 灵活但侵入性强，每个 Controller 都要写 |
| Servlet Filter | 太底层，拿不到 Spring MVC 的异常上下文 |
| Spring 6 ProblemDetail（RFC 7807） | 标准化错误格式，但国内前端生态适配少 |

## 四个组件的协作全景

```mermaid
sequenceDiagram
    participant C as Controller
    participant S as Service
    participant BE as BizException
    participant GEH as GlobalExceptionHandler
    participant R as R&lt;T&gt;
    participant RC as ResultCode

    C->>S: 调用业务方法
    alt 正常流程
        S-->>C: 返回业务数据
        C-->>C: R.ok(data)
    else 业务异常
        S->>RC: 取错误码
        S->>BE: throw new BizException(ResultCode.XXX)
        BE->>GEH: 被捕获
        GEH->>R: R.error(code, message)
        GEH-->>C: 返回 R
    else 参数校验失败
        C->>GEH: Spring 抛 MethodArgumentNotValidException
        GEH->>RC: 取 PARAM_ERROR
        GEH->>R: R.error(400, "字段名: 错误信息")
        GEH-->>C: 返回 R
    else 未知异常
        S->>GEH: throw Exception
        GEH->>RC: 取 INTERNAL_ERROR
        GEH->>R: R.error(500, "服务器内部错误")
        GEH-->>C: 返回 R
    end
```

## 设计取舍总结

| 决策 | 选择 | 理由 |
|------|------|------|
| 响应模式 | HTTP 200 + 业务码 | 网关/CDN 不拦截，前端统一解析 |
| 错误码类型 | 枚举 | code + message 绑定，穷举可见 |
| 异常基类 | RuntimeException | 不污染接口签名，Spring 事务自动回滚 |
| 异常处理 | `@RestControllerAdvice` | 声明式，不侵入业务代码 |
| 堆栈泄露 | 兜底只返回通用消息 | 安全原则，堆栈只记日志 |
