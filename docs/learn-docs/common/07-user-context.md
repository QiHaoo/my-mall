# 用户上下文基础设施

> 涉及组件：`UserContext` / `UserInfo` / `UserContextFilter` / `@CurrentUser` / `CurrentUserArgumentResolver` / `FeignUserContextAutoConfiguration`
>
> 本文聚焦「为什么这样设计」与「原理」。API 与包结构等"是什么"的信息见 [common-module-design.md §3.11](../../common/common-module-design.md#311-用户上下文基础设施)，对接用法见 [security-integration-guide.md](../../standards/security-integration-guide.md)。

---

## 一、为什么需要用户上下文

### 1.1 问题：Service 层怎么知道是谁在调用

```java
// 没有 UserContext 时：逐层透传 userId
public R<Void> createOrder(@RequestBody OrderDTO dto, Long userId) {  // Controller 拿 userId
    orderService.create(dto, userId);                                  // 传给 Service
    inventoryService.deduct(dto.getSkuId(), dto.getCount(), userId);   // 再传给下游
    couponService.use(dto.getCouponId(), userId);                      // 又传一次
    // ...
}
```

问题：
- **参数污染**：每个方法签名多一个 `userId`，与业务参数混在一起
- **调用链越长越痛**：跨多个 Service 时每层都要透传
- **容易遗漏**：新增方法忘加 `userId` 参数，需要改签名波及调用方

### 1.2 方案：ThreadLocal 上下文

```java
// 有 UserContext 时：在入口写入，任意位置读取
public R<Void> createOrder(@RequestBody OrderDTO dto) {
    orderService.create(dto);                       // 不传 userId
    inventoryService.deduct(dto.getSkuId(), ...);   // 不传 userId
    couponService.use(dto.getCouponId());           // 不传 userId
}

// Service 层按需取
public void create(OrderDTO dto) {
    Long userId = UserContext.getUserId();          // 想用的时候才取
    // ...
}
```

`UserContext` 基于 `ThreadLocal`，请求线程内任意位置可读，请求结束时清理。这是 Spring 生态的通行做法（`RequestContextHolder`、`SecurityContextHolder` 都是类似机制）。

---

## 二、ThreadLocal 的取舍

### 2.1 三种 ThreadLocal 对比

| 实现 | 跨线程传递 | 跨线程池传递 | 性能 | 适用场景 |
|------|-----------|------------|------|---------|
| `ThreadLocal` | ❌ | ❌ | 最快 | 请求线程内同步调用 |
| `InheritableThreadLocal` | ✅（仅子线程创建时） | ❌（线程池复用失效） | 略慢 | 主线程 `new Thread()` 场景 |
| `TransmittableThreadLocal`（阿里 TTL） | ✅ | ✅（需配合 TTL 线程池包装） | 中等 | 复杂异步场景 |

### 2.2 为什么选普通 ThreadLocal

本项目当前所有业务调用均在请求线程内完成（同步 Controller → Service → Mapper），普通 ThreadLocal 足够：

- ✅ 性能最优（无额外开销）
- ✅ 语义清晰（线程内可见，跨线程不可见）
- ❌ 不支持 `@Async` / 线程池（异步场景需显式传参）

### 2.3 何时升级为 TransmittableThreadLocal

如果未来出现以下场景，应升级为 TTL：

- 大量 `@Async` 异步方法需要用户上下文
- CompletableFuture / 线程池中发起 Feign 调用
- 定时任务（XXL-Job）需要继承触发用户的身份

升级方式：把 `UserContext` 的 `ThreadLocal<UserInfo>` 替换为 `TransmittableThreadLocal<UserInfo>`，业务代码无需改动。

---

## 三、请求头透传契约

### 3.1 三个头的命名与语义

| 请求头 | 类型 | 示例 | 必填 |
|--------|------|------|------|
| `X-User-Id` | Long | `1001` | ✅ |
| `X-User-Name` | String | `alice` | ❌ |
| `X-User-Roles` | CSV | `ROLE_ADMIN,ROLE_USER` | ❌ |

### 3.2 为什么是三个头而不是一个

**方案 A**：只透传 `X-User-Id`，业务需要 username/roles 时再查 DB
- ❌ 每次请求多次查 DB，性能差
- ❌ 引入缓存又增加一致性复杂度

**方案 B**：把整个 UserInfo JSON 化塞一个头
- ❌ 头大小不可控（roles 多时膨胀）
- ❌ JSON 解析开销
- ❌ 不利于网关层（网关通常只解析 JWT Claims，逐个头写入更自然）

**方案 C（本项目）**：三个独立头
- ✅ 网关从 JWT Claims 直接映射（`sub` → `X-User-Id`，`username` → `X-User-Name`，`roles` → `X-User-Roles`）
- ✅ 业务侧按需读取，不需要的可以不解析
- ✅ 头大小可控

### 3.3 信任边界

```
┌──────────────┐  JWT  ┌──────────────┐  X-User-*  ┌──────────────┐
│   客户端     │ ────► │   网关       │ ─────────► │  业务服务    │
│              │       │  解析+覆盖头  │            │  信任头      │
└──────────────┘       └──────────────┘            └──────────────┘
                            │                           │
                            │ X-User-*                  │ X-User-*
                            ▼                           ▼
                       ┌──────────────┐           ┌──────────────┐
                       │  mall-auth   │           │  下游服务    │
                       │  签发 JWT    │           │  （Feign）   │
                       └──────────────┘           └──────────────┘
```

**业务服务信任 `X-User-*` 头**，不二次校验 JWT。安全保证靠两层：

1. **网关覆盖**：网关在转发前**覆盖**客户端传入的 `X-User-*` 头（即使客户端伪造也无效）
2. **网络隔离**：K8s NetworkPolicy 限制只有网关和内部服务能访问业务服务，外部无法直接访问

如果业务服务直接暴露（无网关），攻击者可以伪造 `X-User-Id`，这是设计上的强约束。

---

## 四、@CurrentUser 与 ArgumentResolver 原理

### 4.1 Spring MVC 参数解析机制

Spring MVC 处理请求时，Controller 方法的每个参数都由一个 `HandlerMethodArgumentResolver` 解析：

```
请求 → DispatcherServlet → HandlerAdapter
    → 遍历参数，逐个找匹配的 ArgumentResolver
    → resolver.resolveArgument() 返回参数值
    → 反射调用 Controller 方法
```

内置的 resolver 有几十个：`@RequestParam` / `@PathVariable` / `@RequestBody` / `@ModelAttribute` 等。自定义 `@CurrentUser` 就是注册一个新的 resolver。

### 4.2 执行时序

```
1. 启动期：WebMvcConfig.addArgumentResolvers() 注册 CurrentUserArgumentResolver
2. 请求期：
   a. UserContextFilter.doFilterInternal() 解析 X-User-* → UserContext.set(UserInfo)
   b. DispatcherServlet 路由到 Controller 方法
   c. CurrentUserArgumentResolver.supportsParameter() 判断参数是否有 @CurrentUser
   d. CurrentUserArgumentResolver.resolveArgument() 从 UserContext 取值返回
   e. Controller 方法执行
   f. UserContextFilter finally 块 UserContext.clear()
```

### 4.3 supportsParameter 的设计

```java
@Override
public boolean supportsParameter(MethodParameter parameter) {
    if (!parameter.hasParameterAnnotation(CurrentUser.class)) {
        return false;  // 无 @CurrentUser 注解，不处理
    }
    Class<?> type = parameter.getParameterType();
    return Long.class == type || long.class == type || UserInfo.class == type;
}
```

支持的类型显式枚举，写错类型（如 `@CurrentUser String`）会在启动期不报错、运行期不解析（参数拿不到值）——这是 Spring 的设计，resolver 不支持的参数会交给下一个 resolver，最终无 resolver 支持时才报错。

### 4.4 未登录返回 null vs 抛异常

```java
@Override
public Object resolveArgument(...) {
    UserInfo user = UserContext.get();
    if (user == null) {
        if (long.class == type) return 0L;  // 基础类型不能返回 null
        return null;
    }
    // ...
}
```

**为什么不抛异常**：

- 商品详情页允许匿名访问，Controller 方法签名 `@CurrentUser Long userId` 也能拿到 null，按匿名逻辑处理
- 下单接口必须登录，业务层 `if (userId == null) throw UNAUTHORIZED` 主动判断
- 统一抛异常会强迫所有用 `@CurrentUser` 的接口都必须登录，灵活性丧失

**为什么 `long` 返回 0**：Java 基础类型不能赋 null，`Long` 包装类才能。建议用 `Long` 而非 `long`，避免 0 与真实 userId 混淆。

---

## 五、Feign 拦截器透传

### 5.1 RequestInterceptor 触发时机

```
业务代码 feignClient.foo()
    → Feign 动态代理
    → RequestInterceptor.apply(template)   ← 这里写入 X-User-* 头
    → LoadBalancer 选实例
    → HTTP 客户端发送请求
    → 下游服务 UserContextFilter 解析 X-User-* → UserContext
```

`RequestInterceptor` 在请求模板构造完成后、HTTP 发送前触发，所有 Feign 调用都会经过。

### 5.2 为什么业务无感知

`FeignUserContextAutoConfiguration` 通过 `@AutoConfiguration` + `AutoConfiguration.imports` 自动装配，注册了一个全局 `RequestInterceptor` Bean。所有 `@FeignClient` 共享这个拦截器，业务模块**不需要写任何代码**就自动透传用户上下文。

### 5.3 异步线程池的失效问题

```
主线程 UserContext.set(alice)
    → executor.submit(() -> {
        UserContext.get()  // 返回 null！
        feignClient.foo()  // 拦截器读到 null，不透传 X-User-*
      })
```

原因：线程池复用线程，`ThreadLocal` 不自动传递。解决方案：

| 方案 | 做法 | 适用场景 |
|------|------|---------|
| 显式传参 | 主线程取出 `UserInfo`，作为方法参数传入异步任务 | 偶尔一两个异步调用 |
| 手动 set/clear | 异步任务开始时 `UserContext.set(user)`，结束 `clear()` | 中等规模 |
| 升级 TTL | `UserContext` 改用 `TransmittableThreadLocal` + TTL 线程池 | 大量异步场景 |

---

## 六、未登录场景的两种处理模式

### 6.1 严格模式（必须登录）

```java
@PostMapping("/orders")
public R<OrderVO> createOrder(@RequestBody OrderDTO dto, @CurrentUser Long userId) {
    if (userId == null) {
        throw new BizException(ResultCode.UNAUTHORIZED);
    }
    return R.ok(orderService.create(dto, userId));
}
```

适用：下单、修改个人资料、删除资源等敏感操作。

### 6.2 宽松模式（允许匿名）

```java
@GetMapping("/products/{id}")
public R<ProductVO> getProduct(@PathVariable Long id, @CurrentUser Long userId) {
    // userId 可能为 null（匿名），按匿名逻辑处理
    return R.ok(productService.getProduct(id, userId));  // 内部按 userId 是否为 null 走不同分支
}
```

适用：商品详情、首页推荐、公开文章等。

### 6.3 当前阶段 vs 网关落地后

| 阶段 | 严格接口 | 宽松接口 |
|------|---------|---------|
| 当前（无网关鉴权） | 业务层 `if (userId == null) throw UNAUTHORIZED` | 业务层按 null 处理 |
| 网关落地后 | 网关统一拦截（白名单外必须带 JWT），业务层可移除 `if` | 网关白名单放行，业务层逻辑不变 |

迁移时**宽松接口完全无改动**，严格接口可移除冗余的 null 检查（保留也无害）。

---

## 七、小结

| 组件 | 解决的问题 | 设计要点 |
|------|----------|---------|
| `UserContext` | Service 层拿不到用户身份 | ThreadLocal，保留 `getUserId()` 兼容 |
| `UserInfo` | userId 单一，username/roles 缺失 | record 不可变，构造时防御性拷贝 |
| `UserContextFilter` | 入口写入上下文 | `OncePerRequestFilter`，finally 清理 |
| `@CurrentUser` | Controller 显式声明更可读 | 注解 + ArgumentResolver 机制 |
| `CurrentUserArgumentResolver` | 解析注解参数 | 未登录返回 null（不抛异常），业务主动判断 |
| `FeignUserContextAutoConfiguration` | Feign 调用透传用户 | `RequestInterceptor` 自动装配，业务无感知 |

整套机制的设计哲学：**入口写入，任意位置读取，业务无感知透传**。
