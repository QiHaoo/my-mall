# mall-common 公共模块设计

> mall-common 是所有业务微服务共享的基础设施模块：统一响应、异常体系、基础实体、分页基类、
> 用户上下文、对象存储 SDK、MyBatis-Plus / SpringDoc / Jackson 全局配置。
> 业务模块通过 Maven 工程依赖引入本模块，Spring Boot 自动装配机制使配置类自动生效。

---

## 一、包结构

```
com.mymall.common/
├── result/
│   └── R.java                    # 统一响应体
├── exception/
│   ├── ResultCode.java           # 错误码枚举
│   ├── BizException.java         # 业务异常
│   └── GlobalExceptionHandler.java  # 全局异常处理器
├── entity/
│   └── BaseEntity.java           # 实体基类（主键/审计/逻辑删除/乐观锁）
├── query/
│   └── PageQuery.java            # 分页查询基类
├── handler/
│   └── MyMetaObjectHandler.java  # 公共字段自动填充
├── util/
│   └── UserContext.java          # 请求级用户上下文（ThreadLocal）
├── config/
│   ├── MybatisPlusConfig.java    # MP 拦截器 + 全局配置
│   ├── SpringDocConfig.java      # OpenAPI 文档配置
│   ├── JacksonConfig.java        # 序列化配置（Long→String/时间格式）
│   └── (各模块自有 config 不放此处)
└── oss/
    ├── OssProperties.java        # OSS 配置属性
    ├── OssTemplate.java          # MinIO 操作封装
    └── OssAutoConfiguration.java # OSS 自动装配
```

资源目录：

```
mall-common/src/main/resources/
└── META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## 二、自动装配机制

业务模块启动类在 `com.mymall.{module}` 包，默认扫不到 `com.mymall.common` 下的组件。解决方式：

1. **`AutoConfiguration.imports`** 注册自动装配配置类（Spring Boot 3 标准机制）：

   ```
   com.mymall.common.config.MybatisPlusConfig
   com.mymall.common.config.SpringDocConfig
   com.mymall.common.config.JacksonConfig
   com.mymall.common.oss.OssAutoConfiguration
   ```

2. **`MybatisPlusConfig` 上的 `@ComponentScan("com.mymall.common")`** 确保 common 包下所有 `@Component`（如 `MyMetaObjectHandler`、`GlobalExceptionHandler`）被业务模块扫描到。

> 历史问题：曾因未注册自动装配，分页插件 / 自动填充 / Swagger 全部不生效。详见 `docs/other/common-module-gap-analysis.md`（已归档，P0 已修复）。

---

## 三、各组件职责

### 3.1 R — 统一响应体

```java
R.ok(data)                    // R<T> 成功，code=200
R.ok().put("k", v)            // R<Map> 链式多键值
R.error(code, msg)            // 错误响应
```

HTTP 状态码始终 200，业务状态由 `R.code` 表达（200=成功，4xx/5xx=错误）。理由：网关/CDN 会拦截 4xx/5xx，统一 200 + 业务码是电商通行做法。详见 [Controller 规范 §2](../controller-specification.md#二返回值与-http-状态码)。

### 3.2 ResultCode — 错误码枚举

码段规划（与实现一致）：

| 码段 | 模块 |
|------|------|
| 200 | 成功 |
| 400~499 | 通用客户端错误（参数/认证/权限/路由） |
| 500~599 | 通用服务端错误 |
| 40001~49999 | 优惠券服务 |
| 50001~59999 | 商品服务（51001~ 分类，52001~ 对象存储） |
| 60001~69999 | 订单服务 |
| 70001~79999 | 会员服务 |
| 80001~89999 | 库存服务 |

新增业务错误码在此枚举追加，按码段归属模块。

### 3.3 BizException — 业务异常

```java
new BizException(ResultCode.COUPON_NOT_FOUND)
new BizException(ResultCode.STOCK_NOT_ENOUGH, "SKU[" + skuId + "] 库存不足")
new BizException(40010, "该优惠券已被领完")
```

三个构造器：`(ResultCode)`、`(ResultCode, String 自定义消息)`、`(int code, String msg)`。Service 层抛出，由 GlobalExceptionHandler 统一捕获。

### 3.4 GlobalExceptionHandler — 全局异常处理器

`@RestControllerAdvice(basePackages = "com.mymall")`，统一 HTTP 200 + R.code。处理的异常：

| 异常 | R.code | 日志级别 |
|------|--------|---------|
| BizException | 业务码 | WARN |
| MethodArgumentNotValidException（@RequestBody 校验） | 400 | WARN |
| BindException（表单校验） | 400 | WARN |
| ConstraintViolationException（@PathVariable/@RequestParam 校验） | 400 | WARN |
| MissingServletRequestParameterException | 400 | WARN |
| MethodArgumentTypeMismatchException | 400 | WARN |
| HttpMessageNotReadableException（body 解析失败） | 400 | WARN |
| MaxUploadSizeExceededException（文件超限） | 400 | WARN |
| NoResourceFoundException | 404 | WARN |
| HttpRequestMethodNotSupportedException | 405 | WARN |
| Exception（兜底） | 500 | ERROR（带堆栈） |

> **切片测试注意**：`@WebMvcTest` 不扫描 `@RestControllerAdvice`，需 `@Import(GlobalExceptionHandler.class)` 才能验证 BizException → 错误码转换与 200 + R.code 策略。

### 3.5 BaseEntity — 实体基类

所有业务实体应继承本类，复用：

| 字段 | 列 | 注解 | 说明 |
|------|----|------|------|
| id | id | @TableId(ASSIGN_ID) | 雪花算法主键 |
| createTime | create_time | @TableField(INSERT) | 创建时间 |
| updateTime | update_time | @TableField(INSERT_UPDATE) | 更新时间 |
| createBy | create_by | @TableField(INSERT) | 创建人（UserContext 填充） |
| updateBy | update_by | @TableField(INSERT_UPDATE) | 更新人 |
| isDeleted | is_deleted | @TableLogic | 逻辑删除 |
| version | version | @Version | 乐观锁 |

继承后实体类无需重复声明这些字段。建表 DDL 必须有对应列，规范见 [表设计规范](../table-design-specification.md)。

### 3.6 MyMetaObjectHandler — 公共字段自动填充

插入时填 `createTime/updateTime/createBy/updateBy/isDeleted(0)/version(1)`，更新时填 `updateTime/updateBy`。`createBy/updateBy` 取自 `UserContext.getUserId()`。用 `strictInsertFill/strictUpdateFill` 严格模式，已有值不覆盖。

### 3.7 PageQuery — 分页查询基类

`pageNum`（默认 1）、`pageSize`（默认 10，上限 500）。分页 DTO 继承此类。分页插件 `PaginationInnerInterceptor` 已设 `maxLimit=500` 兜底。

### 3.8 UserContext — 用户上下文

`ThreadLocal<Long>` 存当前请求用户 ID。网关鉴权后通过 `X-User-Id` 头透传，各服务 `UserContextFilter`（在 mall-oss 等服务内）解析写入。Service 层调 `UserContext.getUserId()` 获取，无需逐层透传。

> **线程池注意**：普通 ThreadLocal 在 `@Async`/线程池中不传递，异步逻辑需显式传 userId 或升级为 `TransmittableThreadLocal`。当前业务均在请求线程内完成。

### 3.9 全局配置类

- **MybatisPlusConfig**：分页插件（maxLimit=500）+ 乐观锁插件 + GlobalConfig（idType=ASSIGN_ID、逻辑删除字段显式声明）。
- **JacksonConfig**：Long→String（防前端 JS 精度丢失）、LocalDateTime→`yyyy-MM-dd HH:mm:ss`、忽略未知字段。
- **SpringDocConfig**：Swagger UI 基础信息，JWT securityScheme 待 mall-auth 落地后启用。

### 3.10 oss — 对象存储 SDK

`OssProperties`（endpoint/publicBaseUrl/region/上传限制）、`OssTemplate`（MinIO Presigned URL 签发/删除/statObject）、`OssAutoConfiguration`（按 `oss.*` 配置装配，minio 依赖 optional）。供 mall-oss 服务使用，设计详见 [对象存储设计](../product/object-storage-design.md)。

---

## 四、依赖管理

- `mall-common/pom.xml`：Web / Validation / Nacos Config / MyBatis-Plus / MySQL / SpringDoc / hutool / Lombok / MinIO(optional)。
- **代码生成器依赖**（`mybatis-plus-generator` + `velocity`）为 `test` scope，不进 runtime classpath。
- 各业务模块通过工程依赖引入 `mall-common`，自动获得上述全部能力。

---

## 五、与规范文档的关系

| 规范文档 | 关联组件 |
|---------|---------|
| [编码规范](../coding-standards.md) | BizException/GlobalExceptionHandler/BaseEntity/日志/DTO/命名 |
| [Controller 规范](../controller-specification.md) | R/校验/HTTP 200 策略 |
| [表设计规范](../table-design-specification.md) | BaseEntity 对应的 DDL 列 |
| [MyBatis-Plus 代码生成规范](../mybatis-plus-codegen-guide.md) | 实体继承 BaseEntity 的生成 |
| [对象存储设计](../product/object-storage-design.md) | oss 包 |

---

## 六、演进记录

- v1（2026-06-22）：P0+P1 补齐——自动装配修复、BizException/ResultCode/GlobalExceptionHandler、PageQuery、BaseEntity @TableLogic、SpringDoc、hutool 统一。
- v1.1（2026-06-23）：OSS 安全闭环增强（UserContext + Content-Type 校验 + 回调幂等）。
- v1.2（2026-06-24）：生产级闭环——统一 200+业务码、补全异常处理器、Jackson Long→String、BaseEntity 审计字段+@Version、MetaObjectHandler 填充补全、MybatisPlusConfig 全局配置、生成器依赖移出 runtime。
