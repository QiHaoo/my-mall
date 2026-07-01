# 安全与认证授权规范

> 本文档定义 my-mall 项目的安全架构、认证授权机制、接口鉴权规则、敏感数据保护、密钥管理等安全相关规范。
>
> **适用范围**：全部微服务（mall-gateway / mall-auth / mall-product / mall-order / mall-ware / mall-coupon / mall-member / mall-seckill / mall-cart / mall-search / mall-third / mall-admin / mall-oss）。
>
> **技术选型**：Spring Authorization Server 1.3 + Spring Security 6.4 + JWT（HS256 / RS256）+ Redis（Token 黑名单）。
>
> **相关文档**：
> - 业务模块对接指南：[security-integration-guide.md](security-integration-guide.md) —— 业务模块如何使用 UserContext / @CurrentUser / Feign 透传
> - 网关配置：[gateway-config-guide.md](../microservice/gateway-config-guide.md) §8 JWT 鉴权
> - 编码规范：[coding-standards.md](coding-standards.md) §5 日志规范（敏感信息不记录）
> - Controller 规范：[controller-specification.md](controller-specification.md) §8 幂等性
> - 公共模块设计：[common-module-design.md](../common/common-module-design.md) §3.11 用户上下文基础设施

---

## 一、认证授权整体架构

### 1.1 架构总览

```
                         ┌─────────────────────────────────┐
                         │           用户终端               │
                         │  （浏览器 / APP / 管理后台）       │
                         └────────────┬────────────────────┘
                                      │ HTTPS
                                      ▼
                         ┌─────────────────────────────────┐
                         │        Spring Cloud Gateway      │
                         │  （路由 / 限流 / JWT 校验 / 透传）  │
                         └────────────┬────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
                    ▼                 ▼                 ▼
          ┌─────────────────┐ ┌──────────────┐ ┌──────────────┐
          │   mall-auth     │ │ mall-product │ │  mall-order  │
          │                 │ │  (业务服务)   │ │  (业务服务)   │
          │ ・OAuth2 授权端点 │ │              │ │              │
          │ ・Token 颁发/刷新 │ │  从请求头读取  │ │  从请求头读取  │
          │ ・Token 注销     │ │  用户上下文    │ │  用户上下文   │
          │ ・登录/注册      │ │              │ │              │
          └────────┬────────┘ └──────────────┘ └──────────────┘
                   │
                   ▼
          ┌─────────────────┐
          │  mall_member DB │
          │  (会员表/角色表) │
          └─────────────────┘
```

### 1.2 认证流程（OAuth2 授权码模式）

**适用场景**：管理后台（mall-admin-frontend）、未来 C 端 Web 应用。

```
用户 → 前端：输入用户名密码
前端 → mall-auth：POST /auth/login {username, password}
mall-auth → mall-member：Feign 校验用户名密码
mall-member → mall-auth：返回用户信息 + 角色 + 权限
mall-auth → 前端：返回 {accessToken, refreshToken, expiresIn}
前端 → Gateway：请求业务接口，Header: Authorization: Bearer {accessToken}
Gateway → 业务服务：校验 JWT 签名 + 过期时间 → 透传用户上下文
业务服务 → 前端：返回业务数据
```

**Token 过期处理**：

```
前端 → mall-auth：POST /auth/refresh {refreshToken}
mall-auth → Redis：校验 refreshToken 有效性 + 黑名单
mall-auth → 前端：返回新的 {accessToken, refreshToken, expiresIn}
```

**Token 注销**：

```
前端 → mall-auth：POST /auth/logout，Header: Authorization: Bearer {accessToken}
mall-auth → Redis：将 accessToken 加入黑名单（TTL = 剩余有效期）
mall-auth → Redis：删除 refreshToken
mall-auth → 前端：200 OK
```

### 1.3 双 Token 机制

| Token 类型 | 用途 | 有效期 | 存储位置 | 刷新策略 |
|-----------|------|--------|---------|---------|
| accessToken | 业务接口鉴权 | 2 小时 | 前端内存（不存 localStorage） | 过期后用 refreshToken 刷新 |
| refreshToken | 刷新 accessToken | 7 天 | 前端 httpOnly Cookie（防 XSS） | 使用一次后失效，返回新的 refreshToken（滚动刷新） |

> **安全考量**：accessToken 不存 localStorage（XSS 可读取），refreshToken 存 httpOnly Cookie（JS 无法读取）。管理后台因是 SPA，可采用内存存储 accessToken + httpOnly Cookie 存储 refreshToken 的方案。

---

## 二、JWT 设计规范

### 2.1 Header

```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

> **算法选择**：项目初期使用 HS256（对称加密，密钥由 mall-auth 和 mall-gateway 共享）。后续微服务增多、密钥分发成本上升时，迁移到 RS256（非对称加密，mall-auth 持有私钥签发，其余服务持有公钥校验）。迁移方案见 §6 密钥管理。

### 2.2 Payload（Claims 结构）

```json
{
  "sub": "1001",                    // 用户 ID（memberId / adminId）
  "aud": "mall-admin",              // 受众：标识 Token 来源端（mall-admin / mall-app / mall-web）
  "iss": "mymall-auth",             // 签发方：固定 mymall-auth
  "iat": 1719504000,                // 签发时间
  "exp": 1719511200,                // 过期时间（iat + 7200s）
  "jti": "uuid-xxx",                // 唯一标识（用于黑名单注销）
  "username": "admin",              // 用户名
  "nickname": "管理员",              // 昵称
  "roles": ["ROLE_ADMIN"],          // 角色列表
  "permissions": ["product:read", "product:write"]  // 权限列表
}
```

| Claim | 类型 | 说明 | 必填 |
|-------|------|------|------|
| `sub` | String | 用户 ID | ✅ |
| `aud` | String | 受众端标识 | ✅ |
| `iss` | String | 签发方，固定 `mymall-auth` | ✅ |
| `iat` | Long | 签发时间（Unix 秒） | ✅ |
| `exp` | Long | 过期时间（Unix 秒） | ✅ |
| `jti` | String | 唯一标识，用于黑名单注销 | ✅ |
| `username` | String | 用户名 | ✅ |
| `nickname` | String | 昵称 | ❌ |
| `roles` | String[] | 角色列表 | ✅ |
| `permissions` | String[] | 权限列表 | ✅ |

> **permissions 命名规范**：`{模块}:{操作}`，如 `product:read`、`order:create`、`order:cancel`。操作统一用 `read / write / delete / export` 四类，不使用 `get / list / add / edit / remove` 等近义词。

### 2.3 签名与校验

| 环节 | 职责方 | 说明 |
|------|--------|------|
| 签发 | mall-auth | 使用密钥对 Header + Payload 签名 |
| 校验 | mall-gateway | 网关全局过滤器校验签名 + 过期时间 + 黑名单 |
| 信任 | 业务服务 | 业务服务信任网关校验结果，从请求头读取用户上下文（不再二次校验签名） |

> **设计决策**：JWT 校验只在网关执行一次，业务服务不重复校验。理由：① 减少重复计算；② 网关是统一入口，校验逻辑集中；③ 业务服务通过网关透传的请求头获取用户信息（`X-User-Id` / `X-User-Name` / `X-User-Roles`），无需解析 JWT。

### 2.4 Token 黑名单

注销或强制下线时，将 Token 的 `jti` 加入 Redis 黑名单：

```
Key:   jwt:blacklist:{jti}
Value: 1
TTL:   Token 剩余有效期（exp - now）
```

网关校验 JWT 时，检查 `jti` 是否在黑名单中：

```java
// 伪代码
if (redisTemplate.hasKey("jwt:blacklist:" + jti)) {
    return unauthorized("Token 已失效");
}
```

> **为什么不用纯无状态 JWT**：纯无状态 JWT 无法主动失效（只能等自然过期）。引入 Redis 黑名单实现"有状态注销"，在安全性和性能间取得平衡——正常请求只需一次 Redis EXISTS 检查（O(1)），仅注销场景写黑名单。

---

## 三、接口鉴权规范

### 3.1 鉴权链路

```
请求 → Gateway AuthGlobalFilter
         │
         ├─ 1. 路径匹配白名单？ → 是：放行
         │
         ├─ 2. 提取 Authorization Header → 无：401
         │
         ├─ 3. 校验 JWT 签名 + 过期时间 → 失败：401
         │
         ├─ 4. 检查 jti 黑名单 → 命中：401
         │
         ├─ 5. 解析用户信息 → 写入请求头（X-User-Id / X-User-Name / X-User-Roles）
         │
         ├─ 6. 权限校验（路径 → 所需权限 → 用户权限匹配）→ 不匹配：403
         │
         └─ 7. 放行，转发到业务服务
```

### 3.2 白名单路径

以下路径无需 JWT 校验，直接放行：

| 路径 | 说明 |
|------|------|
| `/auth/login` | 登录接口 |
| `/auth/refresh` | 刷新 Token |
| `/auth/register` | 注册接口 |
| `/auth/captcha` | 验证码 |
| `/actuator/health` | 健康检查 |
| `/v3/api-docs/**` | OpenAPI 文档 |
| `/swagger-ui/**` | Swagger UI（仅开发/测试环境） |

> 白名单配置在 mall-gateway 的 `application.yml` 或 Nacos 配置中维护，支持动态更新。

### 3.3 权限模型（RBAC）

采用 **RBAC（Role-Based Access Control）** 模型：

```
用户 ──N:M── 角色 ──N:M── 权限
```

| 实体 | 存储位置 | 说明 |
|------|---------|------|
| 用户 | `mall_member.member` / `mall_member.admin` | C 端用户 / 后台管理员 |
| 角色 | `mall_member.role` | 如 ROLE_ADMIN / ROLE_USER / ROLE_OPERATOR |
| 权限 | `mall_member.permission` | 如 product:read / order:create |
| 用户-角色 | `mall_member.member_role` | 多对多关联 |
| 角色-权限 | `mall_member.role_permission` | 多对多关联 |

**权限校验规则**：
- 网关维护路径与所需权限的映射表（从 Nacos 动态加载）
- 用户 JWT 中的 `permissions` 字段包含其全部权限
- 网关校验：`用户权限 ∩ 所需权限 ≠ ∅` 则放行
- `ROLE_ADMIN` 拥有全部权限（超管）

### 3.4 用户上下文透传

网关校验通过后，将用户信息写入请求头，业务服务通过 `UserContext` 读取：

**网关侧（写入请求头）**：

```java
// AuthGlobalFilter 中
ServerHttpRequest mutated = exchange.getRequest().mutate()
    .header("X-User-Id", userId)
    .header("X-User-Name", username)
    .header("X-User-Roles", String.join(",", roles))
    .build();
```

**业务服务侧（读取请求头 → UserContext）**：

```java
// mall-common 中的 UserContextFilter（已有）
@Component
public class UserContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-User-Name");
        String roles = request.getHeader("X-User-Roles");
        UserContext.set(new UserInfo(userId, username, roles));
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();  // ThreadLocal 必须清理，防止线程池泄漏
        }
    }
}
```

> **安全要求**：业务服务必须信任网关透传的请求头，不得自行从外部请求中读取这些头（防止伪造）。实现方式：网关在转发前**覆盖**这些头（即使客户端传了也覆盖），业务服务的 `UserContextFilter` 只读取网关透传的头。生产环境可在网关配置中移除客户端传入的 `X-User-*` 头，再写入网关解析的值。

### 3.5 Feign 调用的用户上下文传递

服务间 Feign 调用需透传用户上下文：

```java
// mall-common 中的 FeignRequestInterceptor
@Bean
public RequestInterceptor requestInterceptor() {
    return template -> {
        UserInfo user = UserContext.get();
        if (user != null) {
            template.header("X-User-Id", user.getUserId());
            template.header("X-User-Name", user.getUsername());
            template.header("X-User-Roles", String.join(",", user.getRoles()));
        }
    };
}
```

> **安全要求**：Feign 调用的目标服务同样信任 `X-User-*` 头。由于 Feign 调用不经过网关，需确保服务间网络隔离（K8s NetworkPolicy），防止外部直接访问业务服务。

---

## 四、敏感数据保护

### 4.1 密码加密

| 项 | 规范 |
|----|------|
| 算法 | BCrypt（Spring Security 默认） |
| 强度 | cost = 10（`BCryptPasswordEncoder(10)`），单次哈希约 100ms |
| 盐 | BCrypt 自带随机盐，无需单独存储 |
| 传输 | 密码必须通过 HTTPS 传输，禁止 HTTP 明文 |
| 存储 | 数据库只存 BCrypt 哈希值，禁止明文存储 |
| 校验 | `passwordEncoder.matches(rawPassword, encodedPassword)` |
| 修改 | 修改密码时，新密码同样 BCrypt 加密后存储 |

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}
```

### 4.2 敏感字段加密存储

| 字段 | 加密方式 | 说明 |
|------|---------|------|
| 密码 | BCrypt | 不可逆 |
| 手机号 | AES-256（ECB / CBC） | 可逆，需查询时解密；或存储脱敏值 + 单独存储哈希值用于查询 |
| 身份证号 | AES-256 | 同上 |
| 银行卡号 | AES-256 | 同上 |
| 邮箱 | 明文（非高敏感） | 仅在 C 端用户场景按需加密 |

> **手机号查询方案**：存储脱敏手机号 `138****8888`（用于展示）+ AES 加密手机号（用于精确匹配）+ 手机号 SHA-256 哈希（用于索引查询）。查询时对输入手机号做 SHA-256 哈希，匹配哈希值定位记录，再用 AES 密钥解密。这样既支持查询又保证安全。

### 4.3 敏感数据脱敏展示

接口返回敏感数据时必须脱敏：

| 字段 | 脱敏规则 | 示例 |
|------|---------|------|
| 手机号 | 中间 4 位星号 | `138****8888` |
| 身份证号 | 中间 8 位星号 | `110***********1234` |
| 银行卡号 | 仅显示后 4 位 | `**** **** **** 8888` |
| 邮箱 | @ 前仅显示首字符 | `a****@example.com` |
| 真实姓名 | 仅显示首字 | `张**` |

**实现方式**：在 VO 类字段上使用脱敏注解 `@Sensitive(type = SensitiveType.PHONE)`，由 Jackson 序列化时自动脱敏。

### 4.4 日志脱敏

详见 [coding-standards.md](coding-standards.md) §5.3，此处补充具体规则：

| 规则 | 说明 |
|------|------|
| 禁止记录 | 密码、Token、身份证号、银行卡号、CVV |
| 脱敏记录 | 手机号（`138****8888`）、邮箱（`a****@example.com`） |
| 实现方式 | 日志脱敏注解 `@LogSensitive` 或 Logback 自定义 PatternLayout 正则替换 |

---

## 五、常见攻击防护

### 5.1 SQL 注入

| 规则 | 说明 |
|------|------|
| **强制使用 `#{}` 参数占位符** | MyBatis 的 `#{}` 会预编译为 PreparedStatement 参数，自动转义 |
| **禁止使用 `${}` 拼接用户输入** | `${}` 是字符串拼接，存在 SQL 注入风险 |
| `${}` 仅限场景 | 表名、列名、ORDER BY 字段等**无法参数化**的场景，且必须用白名单校验 |
| MyBatis-Plus QueryWrapper | `eq / like / in` 等方法内部使用 `#{}`，安全 |
| 自定义 XML SQL | 手写 `${}` 的地方必须 Review，确认输入来源受控 |

**反例（禁止）**：
```xml
<!-- 禁止：用户输入直接拼接 -->
<select id="search">
    SELECT * FROM product WHERE name LIKE '%${keyword}%'
</select>
```

**正例**：
```xml
<select id="search">
    SELECT * FROM product WHERE name LIKE CONCAT('%', #{keyword}, '%')
</select>
```

**ORDER BY 白名单校验**：
```java
// 仅允许合法的排序字段
private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "create_time", "update_time", "sort");

public List<Product> list(String sortField, String sortOrder) {
    if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
        sortField = "id";  // 非法字段回退默认值
    }
    // 此处 ${} 安全：输入已过白名单校验
    return mapper.list(sortField, sortOrder);
}
```

### 5.2 XSS 防护

| 层 | 措施 |
|----|------|
| 前端 | Vue 默认对插值表达式 `{{ }}` 做 HTML 转义；禁止使用 `v-html` 渲染用户输入 |
| 后端 | 输入校验（`@Pattern` / `@Size`）；对富文本字段使用 OWASP Java HTML Sanitizer 过滤 |
| 网关 | 不处理 XSS（交给前后端各自防护） |
| Cookie | 设置 `HttpOnly` + `SameSite=Lax`（防 CSRF） |

### 5.3 CSRF 防护

| 场景 | 措施 |
|------|------|
| API 接口（RESTful） | 使用 JWT Bearer Token 认证，无 Cookie Session，天然免疫 CSRF |
| 管理后台 | 如使用 Cookie 传输 refreshToken，需设置 `SameSite=Lax` + 后端校验 `Referer` / `Origin` |

### 5.4 接口限流

| 层 | 工具 | 策略 |
|----|------|------|
| 网关 | Sentinel | 接口级 QPS 限流（如登录接口 5 次/秒/IP） |
| 业务服务 | Resilience4j | 方法级限流（如秒杀接口令牌桶） |
| 登录接口 | Redis + 滑动窗口 | 防暴力破解：同一 IP 5 次失败后锁定 15 分钟 |

**登录防暴力破解**：

```
Key:   login:fail:{ip}
Value: 失败次数
TTL:   15 分钟

→ 失败次数 ≥ 5：返回 429 "登录失败次数过多，请 15 分钟后重试"
→ 登录成功：清除计数
```

### 5.5 越权访问防护

| 类型 | 说明 | 防护措施 |
|------|------|---------|
| 水平越权 | 用户 A 访问用户 B 的数据 | 接口层校验资源所属用户（`userId == UserContext.getUserId()`） |
| 垂直越权 | 普通用户访问管理员接口 | 网关 RBAC 权限校验 + 业务服务 `@PreAuthorize` 二次校验 |

**水平越权校验示例**：
```java
@GetMapping("/orders/{orderId}")
public R<OrderVO> getOrder(@PathVariable Long orderId) {
    Long currentUserId = UserContext.getUserId();
    Order order = orderService.getById(orderId);
    // 校验订单归属
    if (!order.getMemberId().equals(currentUserId)) {
        throw new BizException(ResultCode.FORBIDDEN, "无权访问该订单");
    }
    return R.success(order);
}
```

---

## 六、密钥管理

### 6.1 密钥清单

| 密钥 | 用途 | 存储位置 | 轮换周期 |
|------|------|---------|---------|
| JWT 签名密钥（HS256） | JWT 签名与校验 | Nacos 配置（加密配置项） | 90 天 |
| AES 加密密钥 | 敏感字段加密 | Nacos 配置（加密配置项） | 180 天 |
| Redis 密码 | Redis 认证 | Nacos 配置 | 按需 |
| MySQL 密码 | 数据库认证 | Nacos 配置 | 按需 |
| Harbor 凭证 | 镜像仓库 | GitHub Secrets | 按需 |
| 第三方 API Key | 短信/支付/OSS | Nacos 配置（加密配置项） | 按服务商要求 |

### 6.2 密钥存储规范

| 环境 | 存储方式 | 说明 |
|------|---------|------|
| 本地开发 | `application.yml` 明文（仅本地） | 本地环境密码可简化，但不能提交到 Git（`.gitignore` 排除 `application-local.yml`） |
| 测试/生产 | Nacos 配置中心 | 使用 Nacos 命名空间隔离环境，密钥配置项加密存储 |
| CI/CD | GitHub Secrets | Harbor 凭证、RELEASE_TOKEN 等，不进 Git |

> **禁止行为**：密钥禁止硬编码在代码中、禁止提交到 Git 仓库、禁止出现在日志中、禁止通过 Feign 接口参数传递。

### 6.3 密钥轮换

JWT 密钥轮换流程（HS256）：

1. 在 Nacos 中配置新密钥（`jwt.secret.new`），保留旧密钥（`jwt.secret.old`）
2. mall-auth 使用新密钥签发 Token
3. mall-gateway 同时支持新旧密钥校验（先试旧密钥，失败再试新密钥）
4. 等待 accessToken 过期周期（2 小时），所有 Token 自然刷新为新密钥签发
5. 移除旧密钥配置

> **RS256 迁移方案**：当微服务增多时，从 HS256 迁移到 RS256。mall-auth 持有私钥签发，网关和业务服务持有公钥校验。公钥可通过 JWK Set 端点（`/oauth2/jwks`）发布，各服务自动拉取。

### 6.4 密钥泄露应急

| 步骤 | 操作 |
|------|------|
| 1 | 立即吊销泄露的密钥，生成新密钥 |
| 2 | 将所有未过期的 JWT 加入 Redis 黑名单（强制全员重新登录） |
| 3 | 检查访问日志，排查异常请求 |
| 4 | 通知所有用户重新登录 |
| 5 | 复盘泄露原因，修复漏洞 |

---

## 七、CORS 安全配置

网关 CORS 配置（已有，补充安全说明）：

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins:
              - 'http://localhost:5173'    # 开发环境
              - 'http://localhost:5174'
              - 'http://localhost:5175'
              - 'http://localhost:5176'
              # 生产环境：精确配置域名，禁止 *
            allowed-methods: GET, POST, PUT, DELETE, OPTIONS
            allowed-headers: '*'
            allow-credentials: true         # 允许携带 Cookie（refreshToken）
            max-age: 3600                   # 预检请求缓存 1 小时
```

| 规则 | 说明 |
|------|------|
| `allowed-origins` | 精确配置域名，**禁止使用 `*`**（`allow-credentials: true` 时 `*` 不生效且不安全） |
| `allowed-methods` | 仅开放必要的 HTTP 方法 |
| `allow-credentials` | `true`（需要 Cookie 传输 refreshToken） |
| 生产环境 | `allowed-origins` 配置精确域名（如 `https://admin.mymall.com`），移除 localhost |

---

## 八、mall-auth 服务实现设计

### 8.1 核心接口

| 接口 | 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|------|
| 登录 | POST | `/auth/login` | 用户名密码登录，返回双 Token | ❌ 白名单 |
| 刷新 Token | POST | `/auth/refresh` | refreshToken 刷新 accessToken | ❌ 白名单 |
| 注销 | POST | `/auth/logout` | Token 加入黑名单 | ✅ |
| 注册 | POST | `/auth/register` | C 端用户注册 | ❌ 白名单 |
| 获取验证码 | GET | `/auth/captcha` | 图形验证码 | ❌ 白名单 |
| 获取当前用户 | GET | `/auth/me` | 返回当前登录用户信息 | ✅ |

### 8.2 错误码规划

| 错误码 | 说明 |
|--------|------|
| 40101 | 用户名或密码错误 |
| 40102 | 账号已禁用 |
| 40103 | 账号已锁定（登录失败次数过多） |
| 40104 | 验证码错误 |
| 40105 | Token 无效 |
| 40106 | Token 已过期 |
| 40107 | Token 已失效（黑名单） |
| 40108 | refreshToken 无效 |
| 40109 | 无权限访问该资源 |
| 40110 | 请求缺少认证信息 |

### 8.3 数据库设计

mall-auth 不独立建库，认证数据存储在 `mall_member`：

| 表 | 说明 |
|----|------|
| `member` | C 端用户表（手机号、密码哈希、状态） |
| `admin` | 后台管理员表（用户名、密码哈希、状态） |
| `role` | 角色表 |
| `permission` | 权限表 |
| `member_role` | 用户-角色关联 |
| `admin_role` | 管理员-角色关联 |
| `role_permission` | 角色-权限关联 |

> 认证授权涉及的用户/角色/权限数据归属 mall-member 服务管理，mall-auth 通过 Feign 调用 mall-member 获取认证数据。

---

## 九、安全检查清单

开发和 Code Review 时逐项检查：

### 认证授权
- [ ] 所有非白名单接口都经过网关 JWT 校验
- [ ] JWT Claims 包含 `sub / aud / iss / iat / exp / jti / roles / permissions`
- [ ] Token 过期时间合理（accessToken 2h / refreshToken 7d）
- [ ] 注销时将 Token 加入 Redis 黑名单
- [ ] refreshToken 一次性使用（滚动刷新）

### 权限控制
- [ ] 接口配置了所需权限（网关路径权限映射表）
- [ ] 业务服务从 `UserContext` 读取用户信息，不自行解析 JWT
- [ ] 涉及用户数据的接口校验数据归属（防水平越权）
- [ ] 管理接口校验管理员角色（防垂直越权）

### 数据保护
- [ ] 密码使用 BCrypt 加密存储
- [ ] 手机号/身份证/银行卡等敏感字段加密存储
- [ ] 接口返回的敏感数据脱敏
- [ ] 日志中不出现密码/Token/身份证号等敏感信息

### 注入防护
- [ ] SQL 使用 `#{}` 参数占位符，禁止 `${}` 拼接用户输入
- [ ] `${}` 仅用于表名/列名/排序字段，且有白名单校验
- [ ] 前端禁止 `v-html` 渲染用户输入
- [ ] 富文本字段使用 HTML Sanitizer 过滤

### 密钥管理
- [ ] 密钥不在代码中硬编码
- [ ] 密钥不提交到 Git 仓库
- [ ] 生产环境密钥存储在 Nacos 加密配置项
- [ ] CI/CD 凭证存储在 GitHub Secrets

### 网络安全
- [ ] CORS `allowed-origins` 精确配置域名，不使用 `*`
- [ ] Cookie 设置 `HttpOnly` + `SameSite=Lax`
- [ ] 生产环境强制 HTTPS
- [ ] 服务间网络隔离（K8s NetworkPolicy，业务服务不对外暴露）
