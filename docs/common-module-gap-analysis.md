# mall-common 模块缺失项分析

> 分析时间：2026-06-21  
> 目的：梳理 common 模块在正式开发业务代码前需要补充的内容，按优先级分档

---

## 一、common 模块现状

### 已有内容

| 文件 | 说明 |
|------|------|
| `result/R.java` | 统一响应体，支持泛型 + 链式 `put()` |
| `base/BaseEntity.java` | 基础 Entity（`id`/`createTime`/`updateTime`，`@Data` + `@Entity` 有问题） |
| `config/MybatisPlusConfig.java` | MyBatis-Plus 配置（分页插件、**未生效**） |
| `handler/MyMetaObjectHandler.java` | 自动填充 `createTime`/`updateTime`（**未生效**） |
| `pom.xml` | 依赖声明（MyBatis-Plus、hutool、SpringDoc、Lombok） |

### 关键问题

`MybatisPlusConfig` 和 `MyMetaObjectHandler` 在 `com.mymall.common.config` 包下，
业务模块的 Spring Boot 启动类在 `com.mymall.coupon` 等包下，**默认包扫描扫不到 common 的类**，
导致分页插件和自动填充完全不生效。

---

## 二、缺失项分档

### P0 — 阻断性 Bug（必须立刻修，否则后续开发全错）

#### P0-1：包扫描缺失，common 配置类不生效

**现象：**
- `Page<>` 分页查询返回全部数据（分页插件没加载）
- 新增/修改记录时 `createTime`/`updateTime` 为 `null`（自动填充没触发）

**根因：**
Spring Boot 默认只扫描启动类所在包及其子包。`MybatisPlusConfig` 在 `com.mymall.common.config`，
业务启动类在 `com.mymall.coupon`，两者不在同一包树，配置类不会被加载。

**修复方案：**
在 common 模块创建 Spring Boot 3 自动装配文件：

```
mall-common/src/main/resources/
└── META-INF/
    └── spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

内容（每行一个配置类全限定名）：
```
com.mymall.common.config.MybatisPlusConfig
```

> 注意：`MybatisPlusConfig` 上有 `@Configuration`，Spring Boot 3 自动装配会将其注册为 Bean。
> `MyMetaObjectHandler` 上有 `@Component`，也会被自动装配扫描到（前提是 `MybatisPlusConfig` 生效）。

---

### P1 — 正式开发前必须补齐

#### P1-1：BizException（业务异常）

Service 层抛异常的标准方式，目前完全缺失。

```java
// 参考 coding-standards.md 中的设计
public class BizException extends RuntimeException {
    private final int code;
    public BizException(int code, String message) { ... }
    public BizException(ResultCode resultCode) { ... }
}
```

#### P1-2：ResultCode 枚举（错误码）

替代裸传 `R.error(500, "xxx")`，统一错误码管理。

```java
// 参考 coding-standards.md 中的码段规划
public enum ResultCode {
    SUCCESS(0, "成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    // 业务错误码 40001+
    COUPON_NOT_FOUND(40001, "优惠券不存在"),
    STOCK_NOT_ENOUGH(40002, "库存不足"),
    ;
    private final int code;
    private final String message;
}
```

#### P1-3：GlobalExceptionHandler（全局异常处理器）

Controller 不用写 try-catch，统一处理所有异常。

```java
// 参考 coding-standards.md 中的完整实现
@RestControllerAdvice(basePackages = "com.mymall")
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) { ... }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationException(...) { ... }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) { ... }
}
```

#### P1-4：PageQuery（分页查询基类）

所有分页查询的 DTO 应继承此类，统一分页参数。

```java
@Data
public class PageQuery {
    @Schema(description = "页码", defaultValue = "1")
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    @Schema(description = "每页数量", defaultValue = "10")
    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 500, message = "每页数量最大为500")
    private Integer pageSize = 10;
}
```

#### P1-5：BaseEntity 补充 @TableLogic

`application.yml` 已配置逻辑删除字段 `is_deleted`，但 `BaseEntity` 没有对应的注解，
逻辑删除功能不生效（删除操作会物理删除）。

```java
@Data
public abstract class BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic  // ← 缺失，补上
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted = 0;
}
```

#### P1-6：BaseEntity 的 @Entity 注解错误

`BaseEntity` 上有 `@Entity`（JPA 注解），但项目用的是 MyBatis-Plus，
这个注解多余且可能引起混淆，应移除。

#### P1-7：SpringDoc OpenAPI 配置

`pom.xml` 已引入 `springdoc-openapi-starter-webmvc-api`，但没有配置 Bean，
Swagger UI 无法访问（`http://localhost:7000/swagger-ui.html` 返回 404）。

需要在 common 模块提供一个自动装配的配置类：

```java
@Configuration
public class SpringDocConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info().title("my-mall API").version("v1"));
    }
}
```

#### P1-8：hutool 版本统一

`mall-common/pom.xml` 硬编码了 hutool 版本 `5.8.32`，应统一到父 POM 的
`<dependencyManagement>` 中管理，避免各模块版本不一致。

---

### P2 — 建议补齐（业务开发到一定阶段后补）

| # | 缺失项 | 说明 |
|---|--------|------|
| P2-1 | **CORS 跨域配置** | 前端联调必须，网关层或各服务都需要 |
| P2-2 | **CommonConstants** | 常量类（缓存 key 前缀、header 名、幂等 token 名等） |
| P2-3 | **UserContext** | `ThreadLocal` 用户上下文，Feign / 网关透传用户信息用 |
| P2-4 | **BaseEntity 补充 @Version** | `MybatisPlusConfig` 已启用乐观锁插件，但 `BaseEntity` 没有 `version` 字段 |

---

### P3 — 后期逐步补充

| # | 缺失项 | 说明 |
|---|--------|------|
| P3-1 | Feign 拦截器 | 透传 `traceId` / `token` |
| P3-2 | Redis 工具类 | 封装常用操作（`CacheHelper` 或 `RedisService`） |
| P3-3 | Jackson 序列化配置 | `LocalDateTime` 格式化、`null` 值处理等 |
| P3-4 | common 模块单元测试 | `R`、`BaseEntity` 等的测试 |
| P3-5 | Nacos 共享配置 | 公共配置通过 Nacos data-id 共享，避免各模块重复配置 |

---

## 三、建议的处理顺序

```
第一步：修 P0 包扫描 Bug（5 分钟）
  └─ 创建 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

第二步：补 P1 全部（约 1-2 小时）
  ├─ BizException + ResultCode 枚举 + GlobalExceptionHandler（一组，互相依赖）
  ├─ PageQuery 分页基类
  ├─ BaseEntity 修复（@TableLogic、移除 @Entity）
  ├─ SpringDoc OpenAPI 配置类
  └─ hutool 版本统一到父 POM

第三步：补 P2 按需（约 30 分钟）
  ├─ CORS 配置（CorsFilter 或 @CrossOrigin）
  ├─ CommonConstants 常量类
  └─ UserContext（ThreadLocal 工具类）

第四步：验证
  ├─ 启动 coupon 服务，调用分页接口，确认分页生效
  ├─ 新增一条记录，确认 createTime 自动填充
  └─ 访问 Swagger UI，确认接口文档可查看

第五步：开始 product 模块开发
```

---

## 四、各模块 pom.xml 依赖检查

### 当前各模块已引入的 common 相关依赖

| 模块 | common 依赖 | 备注 |
|------|-------------|------|
| mall-coupon | `mall-common`（工程依赖） | 已有 |
| mall-member | `mall-common`（工程依赖） | 已有 |

自动装配生效的前提：`mall-common` 的 `META-INF/spring/...imports` 文件存在，
且业务模块通过 `pom.xml` 引入了 `mall-common`（工程依赖），Maven 依赖传递会自动触发自动装配。

> **验证方式**：在业务模块启动类的 `main` 方法里加一行
> `System.out.println(ApplicationContext.getBean(MybatisPlusConfig.class))`，
> 如果不报错说明自动装配生效了。

---

## 五、补充说明

### 关于 MyMetaObjectHandler 的生效条件

`MyMetaObjectHandler` 上有 `@Component`，但它能否被扫描到取决于：

1. **MybatisPlusConfig 被加载**（作为 `@Configuration` 类被 Spring 扫描）→ 触发自动装配后满足
2. **MybatisPlusConfig 上是否有 `@ComponentScan`** → 目前没有，但 `@Configuration` 类本身会被 Spring 的 `ConfigurationClassPostProcessor` 处理，`@Component` 的类如果和 `@Configuration` 在同一包或子包会被扫到

**更稳妥的方案**：在 `MybatisPlusConfig` 上加 `@ComponentScan("com.mymall.common")`，
确保 common 包下的所有 `@Component` 都被扫描到。

### 关于 Page 分页插件不生效的现象

如果 P0 不修复，调用 `couponService.page(new Page<>(1, 10), wrapper)` 会返回全部数据，
相当于 `LIMIT` 子句没生成，分页完全失效。这是最容易被忽略但影响最大的 Bug。

---

*文档生成时间：2026-06-21*  
*对应分析会话：用户要求梳理 common 模块缺失项，按生产级标准分 P0/P1/P2/P3 四档*
