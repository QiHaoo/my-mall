# 认证授权对接指南

> 本文档面向**业务模块开发者**（mall-product / mall-order / mall-member / mall-cart 等），指导如何在业务代码中对接 my-mall 的认证授权体系。
>
> **配套文档**：
> - 安全架构与设计：[security-specification.md](security-specification.md) —— OAuth2 流程、JWT 设计、RBAC、密钥管理等完整设计
> - 编码规范：[coding-standards.md](coding-standards.md) §4 异常体系
> - Controller 规范：[controller-specification.md](controller-specification.md)
> - 公共模块设计：[common-module-design.md](../common/common-module-design.md) §3.11 用户上下文基础设施
> - 学习笔记：[learn-docs/common/07-user-context.md](../learn-docs/common/07-user-context.md) —— 设计原理与取舍

---

## 一、背景与边界

### 1.1 当前状态

| 模块 | 状态 |
|------|------|
| mall-common 基础设施 | ✅ 已落地（`UserContext` / `UserInfo` / `UserContextFilter` / `@CurrentUser` / `FeignUserContextInterceptor`） |
| mall-auth 登录与 JWT 签发 | ❌ 未落地 |
| mall-gateway 鉴权过滤器 | ❌ 未落地 |
| 用户/角色/权限表 | ❌ 未落地 |
| Spring Security 资源服务器 | ❌ 未落地 |

### 1.2 当前阶段业务模块需要做什么

业务模块**只需要使用 mall-common 提供的基础设施**，不需要也不应该自行实现认证逻辑。具体：

- ✅ 用 `@CurrentUser` / `UserContext` 获取当前登录用户
- ✅ 用 `BizException(ResultCode.UNAUTHORIZED)` / `BizException(ResultCode.FORBIDDEN)` 拒绝未授权访问
- ✅ 用水平/垂直越权校验保护用户数据
- ✅ Feign 调用自动透传用户上下文（基础设施已实现，业务无感知）
- ✅ 审计字段（`createBy` / `updateBy`）由 `MyMetaObjectHandler` 自动填充
- ❌ 不解析 JWT（信任网关）
- ❌ 不读取 `Authorization` 请求头
- ❌ 不自行实现密码校验、Token 签发
- ❌ 不在业务模块引入 `spring-security` 依赖

### 1.3 认证授权落地后的迁移路径

当 mall-auth 与 mall-gateway 鉴权过滤器落地后，业务模块**无需改动**：

1. 网关解析 JWT → 写入 `X-User-Id` / `X-User-Name` / `X-User-Roles` 请求头
2. 业务服务的 `UserContextFilter` 解析请求头 → 写入 `UserContext`（已就绪）
3. 业务代码通过 `@CurrentUser` / `UserContext` 取用户（已就绪）

唯一的迁移点：从「业务层主动校验未登录抛 UNAUTHORIZED」改为「网关统一拦截白名单外的请求」。详见 §10。

---

## 二、快速对接清单

业务模块开发时按下表逐项确认：

| # | 项 | 做法 | 是否必做 |
|---|---|------|---------|
| 1 | 获取当前用户 ID | `@CurrentUser Long userId` 或 `UserContext.getUserId()` | 按需 |
| 2 | 获取完整用户信息 | `@CurrentUser UserInfo user` | 按需 |
| 3 | 未登录拒绝 | `throw new BizException(ResultCode.UNAUTHORIZED)` | 必做（涉及登录态的接口） |
| 4 | 水平越权校验 | 校验资源 `userId == UserContext.getUserId()` | 必做（涉及用户数据的接口） |
| 5 | 垂直越权校验 | 校验角色或权限（当前阶段抛 `FORBIDDEN`，未来用 `@PreAuthorize`） | 必做（管理接口） |
| 6 | Feign 调用透传用户 | 无需写代码，`FeignUserContextInterceptor` 自动透传 | 已自动 |
| 7 | 审计字段填充 | 实体继承 `BaseEntity`，`createBy`/`updateBy` 自动填 | 已自动 |

---

## 三、获取当前用户

### 3.1 两种获取方式

| 方式 | 适用场景 | 示例 |
|------|---------|------|
| `@CurrentUser` 注解（Controller 入参） | Controller 层显式声明，可读性强 | `public R<Void> foo(@CurrentUser Long userId)` |
| `UserContext.getUserId()` / `UserContext.get()` | Service / Handler / 任意位置 | `Long userId = UserContext.getUserId();` |

**推荐**：Controller 入参用 `@CurrentUser`，Service 层用 `UserContext`。

### 3.2 Controller 入参方式

```java
import com.mymall.common.web.CurrentUser;
import com.mymall.common.util.UserInfo;

@GetMapping("/orders/{orderId}")
public R<OrderVO> getOrder(@PathVariable Long orderId,
                           @CurrentUser Long currentUserId) {
    if (currentUserId == null) {
        throw new BizException(ResultCode.UNAUTHORIZED);
    }
    return R.ok(orderService.getOrderForUser(orderId, currentUserId));
}

@GetMapping("/me")
public R<UserInfo> me(@CurrentUser UserInfo user) {
    if (user == null) {
        throw new BizException(ResultCode.UNAUTHORIZED);
    }
    return R.ok(user);
}
```

支持的参数类型：

| 参数类型 | 已登录 | 未登录 |
|---------|-------|-------|
| `Long userId` | 返回 userId | 返回 `null` |
| `long userId`（基础类型，不推荐） | 返回 userId | 返回 `0` |
| `UserInfo user` | 返回完整对象 | 返回 `null` |

### 3.3 Service 层方式

```java
import com.mymall.common.util.UserContext;

public OrderVO getOrderForUser(Long orderId, Long userId) {
    // 直接用 UserContext 也可以，但参数透传更清晰、可测试性更好
    Long currentUserId = UserContext.getUserId();
    // ...
}
```

**推荐**：Service 方法签名透传 `userId` 参数，便于单元测试（不依赖 ThreadLocal）。Controller 取出后传入。

### 3.4 未登录场景处理

`@CurrentUser` / `UserContext` 在未登录时返回 `null`（`long` 基础类型返回 `0`）。业务层**主动**判断并抛异常：

```java
// 严格接口（必须登录）：商品下单、修改个人资料
if (userId == null) {
    throw new BizException(ResultCode.UNAUTHORIZED);
}

// 宽松接口（允许匿名）：商品详情、首页推荐
// 不抛异常，按匿名逻辑处理
```

**设计理由**：登录态校验由业务主动判断更灵活——商品详情页允许匿名访问，下单接口必须登录，统一拦截反而限制了灵活性。

---

## 四、接口鉴权写法

### 4.1 当前阶段：业务层主动校验

网关鉴权过滤器尚未落地，业务层需要主动校验登录态与权限。

**未登录校验**：
```java
if (UserContext.getUserId() == null) {
    throw new BizException(ResultCode.UNAUTHORIZED);
}
```

**水平越权校验**（防用户 A 访问用户 B 的数据）：
```java
@GetMapping("/orders/{orderId}")
public R<OrderVO> getOrder(@PathVariable Long orderId, @CurrentUser Long currentUserId) {
    if (currentUserId == null) {
        throw new BizException(ResultCode.UNAUTHORIZED);
    }
    Order order = orderService.getById(orderId);
    if (order == null) {
        throw new BizException(ResultCode.ORDER_NOT_FOUND);
    }
    // 校验订单归属
    if (!order.getMemberId().equals(currentUserId)) {
        throw new BizException(ResultCode.FORBIDDEN, "无权访问该订单");
    }
    return R.ok(orderService.toVO(order));
}
```

**垂直越权校验**（防普通用户访问管理员接口）：
```java
// 当前阶段：业务层抛 FORBIDDEN
@DeleteMapping("/products/{id}")
public R<Void> deleteProduct(@PathVariable Long id, @CurrentUser UserInfo user) {
    if (user == null) {
        throw new BizException(ResultCode.UNAUTHORIZED);
    }
    if (!user.hasRole("ROLE_ADMIN")) {
        throw new BizException(ResultCode.FORBIDDEN, "需要管理员权限");
    }
    productService.removeById(id);
    return R.ok();
}
```

### 4.2 未来阶段：网关鉴权 + Spring Security 注解

mall-auth 与网关鉴权落地后：

| 变化 | 说明 |
|------|------|
| 网关统一拦截 | 白名单外的请求必须携带有效 JWT，否则 401（业务层不再需要主动抛 UNAUTHORIZED） |
| 网关权限校验 | 网关根据路径→权限映射表校验，无权访问 403 |
| 业务层 `@PreAuthorize` | 复杂权限校验仍由业务层做（如数据归属），用 Spring Security 注解 |

```java
// 未来阶段写法（当前不要用，spring-security 依赖未引入）
@PreAuthorize("hasAuthority('product:delete')")
@DeleteMapping("/products/{id}")
public R<Void> deleteProduct(@PathVariable Long id) {
    productService.removeById(id);
    return R.ok();
}
```

> **迁移提示**：当前阶段用 `user.hasRole("ROLE_ADMIN")` 的写法，迁移时替换为 `@PreAuthorize` 注解即可。水平越权校验（`orderId` 归属）始终保留在业务层。

---

## 五、Feign 调用透传用户上下文

### 5.1 自动透传

`FeignUserContextInterceptor`（mall-common 已实现）会自动把当前线程的 `UserContext` 写入 Feign 请求头：

```
当前线程 UserContext → Feign 请求头 X-User-Id/X-User-Name/X-User-Roles → 下游服务 UserContext
```

业务代码**无感知**，正常写 Feign 调用即可：

```java
@FeignClient(name = "mall-member", path = "/member/member")
public interface MemberFeignClient {

    @GetMapping("/{id}")
    R<MemberVO> getById(@PathVariable Long id);
}

// 调用方
memberFeignClient.getById(1001L);  // 自动透传当前用户上下文
```

### 5.2 异步线程池场景的注意

`UserContext` 基于普通 `ThreadLocal`，`@Async` 或自定义线程池**不会自动传递**。异步 Feign 调用会丢失用户上下文。

**错误写法**：
```java
@Async
public void asyncNotify() {
    // UserContext.getUserId() 返回 null
    memberFeignClient.notify(...);  // 透传的也是 null
}
```

**正确写法**：在主线程取出 userId 后作为参数传递，或在异步任务中手动 set：

```java
public void notifyAsync() {
    Long userId = UserContext.getUserId();
    UserInfo user = UserContext.get();
    executor.submit(() -> {
        try {
            UserContext.set(user);
            memberFeignClient.notify(...);
        } finally {
            UserContext.clear();
        }
    });
}
```

> **何时升级**：若未来异步场景增多，可将 `UserContext` 升级为 `TransmittableThreadLocal`（阿里 TTL 库），所有线程池自动透传。当前业务均在请求线程内完成，不需要。

---

## 六、审计字段自动填充

`BaseEntity` 的 `createBy` / `updateBy` 由 `MyMetaObjectHandler` 从 `UserContext.getUserId()` 自动填充，业务代码**不需要**手动 set。

```java
// 业务代码
brandService.save(brand);  // brand.createBy 自动填为当前登录用户 ID

// 不要这样写（多余且会被严格模式忽略）
brand.setCreateBy(UserContext.getUserId());
brandService.save(brand);
```

未登录场景（如匿名上传 OSS）`createBy` 填 null，符合预期。

详见 [common-module-design.md](../common/common-module-design.md) §3.6。

---

## 七、错误码使用

### 7.1 鉴权相关错误码

| 错误码 | 含义 | 使用场景 |
|--------|------|---------|
| `ResultCode.UNAUTHORIZED` (401) | 未登录或 token 已过期 | 业务层校验未登录时抛 |
| `ResultCode.FORBIDDEN` (403) | 无权限 | 水平/垂直越权校验失败时抛 |

```java
throw new BizException(ResultCode.UNAUTHORIZED);
throw new BizException(ResultCode.FORBIDDEN, "无权访问该订单");
```

### 7.2 业务专属错误码

各业务模块在 `ResultCode` 枚举按段位追加错误码（已有段位规划见枚举注释）。鉴权相关错误码统一用 401/403，**不要**为业务接口单独定义"未登录"错误码。

### 7.3 错误响应格式

由 `GlobalExceptionHandler` 统一处理，HTTP 状态码始终 200，业务状态由 `R.code` 表达：

```json
{
  "code": 401,
  "msg": "未登录或 token 已过期",
  "data": null
}
```

详见 [coding-standards.md](coding-standards.md) §4 异常体系。

---

## 八、敏感数据脱敏（前瞻）

> 当前未实现，文档先约定用法。mall-auth 落地时统一实施。

接口返回敏感数据（手机号、身份证号等）时，在 VO 字段上标注 `@Sensitive` 注解，由 Jackson 序列化时自动脱敏：

```java
// 未来用法（当前注解未实现）
public class MemberVO {
    @Sensitive(type = SensitiveType.PHONE)
    private String phone;        // 138****8888

    @Sensitive(type = SensitiveType.ID_CARD)
    private String idCard;       // 110***********1234
}
```

当前阶段：业务层自行在 VO 转换时脱敏（如 `phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2")`）。

---

## 九、开发自查清单

Code Review 时逐项检查：

### 鉴权
- [ ] 涉及登录态的接口已校验 `UserContext.getUserId() != null`
- [ ] 涉及用户数据的接口已校验资源归属（防水平越权）
- [ ] 管理接口已校验角色（防垂直越权）
- [ ] 未引入 `spring-security` 依赖（统一由 mall-auth / mall-gateway 处理）

### 用户上下文
- [ ] Controller 用 `@CurrentUser` 而非 `request.getHeader("X-User-Id")`
- [ ] Service 层不直接读 `X-User-Id` 请求头（信任 `UserContext`）
- [ ] 异步任务传递了 `UserInfo` 或手动 set/clear `UserContext`
- [ ] 未在 `finally` 之外的位置调用 `UserContext.clear()`（`UserContextFilter` 已负责清理）

### 数据保护
- [ ] 密码、Token 不记录到日志
- [ ] 手机号、身份证号日志脱敏
- [ ] SQL 用 `#{}` 参数占位符，`${}` 仅用于表名/列名且有白名单校验

### Feign 调用
- [ ] Feign 接口不手动传 `X-User-Id`（拦截器自动透传）
- [ ] 异步 Feign 调用已处理用户上下文传递

---

## 十、待落地清单

认证授权真正落地时，业务模块需要切换的点：

| 项 | 当前 | 落地后 | 影响 |
|---|------|-------|------|
| 未登录校验 | 业务层主动抛 `UNAUTHORIZED` | 网关统一拦截 | 业务层 `if (userId == null) throw UNAUTHORIZED` 可移除，但保留也无害 |
| 权限校验 | 业务层 `user.hasRole(...)` 抛 `FORBIDDEN` | 网关路径权限映射 + `@PreAuthorize` | 简单角色校验交给网关，复杂业务规则保留在业务层 |
| 请求头来源 | 业务服务直接接收（无校验） | 网关覆盖 `X-User-*` 头 | 业务无感知，部署时确保网络隔离 |
| Spring Security | 不引入 | mall-common 引入 `spring-security-oauth2-resource-server` | 业务模块通过 `@PreAuthorize` 注解使用 |
| `@Sensitive` 脱敏 | 业务层手动脱敏 | mall-common 实现 `@Sensitive` + Jackson Serializer | VO 字段加注解替换手动脱敏 |

**迁移原则**：业务模块当前的写法（`@CurrentUser` / `UserContext` / `BizException(UNAUTHORIZED/FORBIDDEN)`）在落地后**无需改动**，新增能力（`@PreAuthorize` / `@Sensitive`）按需引入。
