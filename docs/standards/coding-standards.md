# 编码规范

> 项目级通用编码规范，覆盖 Service / DTO / VO / Entity / 异常处理 / 日志 / 命名。
> Controller 层规范见 [Controller 接口编写规范](./controller-specification.md)。

---

## 一、项目分层架构

```
Controller（HTTP 层）
  │  参数校验 → 调用 Service → 组装返回值
  │  不写业务逻辑，不直接操作数据库
  ▼
Service（业务层）
  │  业务规则、事务控制、跨服务调用
  │  接口 + 实现分离，面向接口编程
  ▼
Mapper（数据访问层）
  │  MyBatis-Plus BaseMapper + 自定义 XML
  │  只做数据读写，不含业务逻辑
  ▼
Entity（数据模型）
  │  与数据库表一一对应
```

### 各层职责边界

| 层 | 允许做的事 | 禁止做的事 |
|----|-----------|-----------|
| Controller | 参数校验、调用 Service、组装 R 返回值 | if/else 业务分支、for 循环处理数据、直接调 Mapper |
| Service | 业务逻辑、事务控制、调 Mapper/Feign | 操作 HttpServletRequest/Response、拼 JSON |
| Mapper | 数据库读写 | 业务逻辑、调其他服务的 Feign |
| Entity | 承载数据 | 业务方法 |

---

## 二、可测试性设计原则

测试好不好写，取决于生产代码的设计。以下原则从源头保证可测试性：

| 原则 | 说明 | 示例 |
|------|------|------|
| **构造器注入** | 统一用构造器注入依赖，不用 `@Autowired` 字段注入 | 便于 `@InjectMocks` 自动注入 |
| **面向接口编程** | 注入接口而非实现类 | Mock 时 mock 接口即可 |
| **避免静态方法调用** | 工具类静态方法难以 Mock，必要时包装成 Bean | 如 `UUID.randomUUID()` → `IdGenerator` Bean |
| **避免在构造器中做业务** | Bean 初始化时不要调远程接口或 DB | 否则测试加载上下文时就报错 |

```java
// ✅ 构造器注入（项目统一方式）
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;        // final + 构造器注入
    private final WareFeignClient wareFeignClient;

    // @RequiredArgsConstructor 自动生成构造器，无需手写
}

// ❌ 字段注入（不推荐，难以单元测试）
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;  // 无法在 new 时注入，无法声明 final
}
```

---

## 三、Service 层规范

### 3.1 接口与实现分离

```java
// 接口：定义业务契约，以 I 开头
public interface OrderService extends IService<Order> {
    Long createOrder(OrderCreateDTO dto);
    void cancelOrder(Long orderId);
}

// 实现：业务逻辑，事务控制
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order>
        implements OrderService {

    private final WareFeignClient wareFeignClient;
    private final CouponFeignClient couponFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderCreateDTO dto) {
        // 业务逻辑
    }
}
```

### 3.2 事务边界

| 规则 | 说明 |
|------|------|
| `@Transactional` 加在 Service 实现类的方法上 | 不加在 Controller 和 Mapper 上 |
| `rollbackFor = Exception.class` 必须指定 | 默认只回滚 RuntimeException，Checked Exception 不回滚 |
| 事务方法不能被同类内部调用 | Spring AOP 基于代理，内部调用不经过代理，事务不生效 |
| 跨服务调用不要放在事务内 | Feign 调用超时会导致事务长时间挂起 |

```java
// ✅ 正确：事务在 Service 层
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderCreateDTO dto) {
        Order order = buildOrder(dto);
        orderMapper.insert(order);           // 本地事务
        wareFeignClient.lockStock(dto);      // 远程调用在事务内（注意超时风险）
        return order.getId();
    }
}

// ❌ 错误：事务在 Controller 层
@PostMapping
@Transactional   // Controller 不应该有事务注解
public R<Void> submit(@RequestBody OrderSubmitDTO dto) {
    orderService.createOrder(dto);
    return R.ok();
}
```

### 3.3 事务自调用问题

```java
// ❌ 错误：同类内部调用，事务不生效
@Service
public class OrderServiceImpl implements OrderService {

    public void batchCreate(List<OrderCreateDTO> dtos) {
        for (OrderCreateDTO dto : dtos) {
            this.createOne(dto);  // 直接调用，不经过代理，@Transactional 无效！
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void createOne(OrderCreateDTO dto) { ... }
}

// ✅ 方案1：拆到两个 Service
// OrderService.batchCreate() → OrderItemService.createOne()

// ✅ 方案2：通过 AopContext 获取代理对象（需要在启动类加 @EnableAspectJAutoProxy(exposeProxy = true)）
((OrderService) AopContext.currentProxy()).createOne(dto);
```

### 3.4 Service 方法命名

| 场景 | 命名规则 | 示例 |
|------|---------|------|
| 查询单个 | `get{Entity}` / `find{Entity}` | `getById`, `findByCouponName` |
| 查询列表 | `list{Entity}` / `page{Entity}` | `listByStatus`, `pageQuery` |
| 新增 | `save` / `create{Entity}` | `save`, `createOrder` |
| 修改 | `update{Entity}` | `updateStatus`, `updateById` |
| 删除 | `remove{Entity}` / `delete{Entity}` | `removeById`, `batchRemove` |
| 业务操作 | `动词{Entity}` | `cancelOrder`, `lockStock`, `deductStock` |

---

## 四、异常处理

### 4.1 异常体系

```
RuntimeException
  └── BizException（业务异常，可预期）
        ├── code: 业务错误码
        └── message: 用户可见的错误消息
```

**HTTP 状态码策略**：本项目统一 HTTP 200 + `R.code` 表达业务状态（200=成功，4xx/5xx=错误）。
理由：电商业务错误种类多，4xx/5xx 会被网关/CDN 拦截导致前端拿不到响应体；统一 200 + 业务码是国内主流电商通行做法。详见 [Controller 规范 §2](./controller-specification.md#二返回值与-http-状态码)。

| 异常类型 | 说明 | HTTP | R.code | 日志级别 |
|---------|------|------|--------|---------|
| `BizException` | 业务规则不满足（库存不足、优惠券过期） | 200 | 业务码 | WARN |
| `MethodArgumentNotValidException` | @RequestBody 校验失败 | 200 | 400 | WARN |
| `BindException` | 表单参数校验失败 | 200 | 400 | WARN |
| `ConstraintViolationException` | @PathVariable/@RequestParam 校验失败 | 200 | 400 | WARN |
| `HttpMessageNotReadableException` | 请求体解析失败 | 200 | 400 | WARN |
| `NoResourceFoundException` | 资源不存在 | 200 | 404 | WARN |
| `HttpRequestMethodNotSupportedException` | 方法不支持 | 200 | 405 | WARN |
| 其他 `Exception` | 未预期异常（NPE、DB 异常） | 200 | 500 | ERROR（带堆栈） |

### 4.2 BizException 实现

放在 `mall-common` 的 `exception` 包下，所有模块共享：

```java
package com.mymall.common.exception;

import lombok.Getter;

/**
 * 业务异常
 *
 * <p>用于表达可预期的业务规则不满足，如库存不足、优惠券已过期等。
 * 由 GlobalExceptionHandler 统一捕获，返回 R.error(code, msg) 给前端。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    /** 自定义 code + message */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 使用 ResultCode 枚举 */
    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 使用 ResultCode 枚举 + 自定义 message（覆盖默认 message） */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
```

### 4.3 GlobalExceptionHandler 实现

放在 `mall-common` 的 `exception` 包下，统一 HTTP 200 + R.code：

```java
package com.mymall.common.exception;

import com.mymall.common.result.R;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>统一 HTTP 200 + R.code 策略，Controller 不需要 try-catch。
 */
@RestControllerAdvice(basePackages = "com.mymall")
public class GlobalExceptionHandler {

    /** 业务异常：可预期，WARN 日志 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return R.error(e.getCode(), e.getMessage());
    }

    /** @RequestBody 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /** 表单参数校验失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) { ... }

    /** @PathVariable/@RequestParam 校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) { ... }

    /** 请求体解析失败 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) { ... }

    /** 资源不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) { ... }

    /** 兜底：未预期异常，记录完整堆栈，不向客户端泄露 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("未处理异常", e);
        return R.error(ResultCode.INTERNAL_ERROR.getCode(),
                       ResultCode.INTERNAL_ERROR.getMessage());
    }
}
```

> 完整处理器清单（含 MaxUploadSizeExceededException、MethodArgumentTypeMismatchException 等）见
> `mall-common/src/main/java/com/mymall/common/exception/GlobalExceptionHandler.java`。
>
> **切片测试**：`@WebMvcTest` 不扫描 `@RestControllerAdvice`，需 `@Import(GlobalExceptionHandler.class)`
> 才能验证异常 → R.code 转换。

### 4.4 使用方式

```java
// Service 中抛业务异常（推荐用 ResultCode 枚举）
public void deductStock(Long skuId, Integer count) {
    Integer stock = wareMapper.getStock(skuId);
    if (stock < count) {
        throw new BizException(ResultCode.STOCK_NOT_ENOUGH, "SKU[" + skuId + "] 库存不足");
    }
}
```

### 4.5 错误码规划

错误码集中在 `ResultCode` 枚举管理，按模块划分码段（与实现一致）：

| 码段 | 模块 | 示例 |
|------|------|------|
| 200 | 成功 | — |
| 400 | 参数错误（全局） | PARAM_ERROR |
| 401 | 未登录 | UNAUTHORIZED |
| 403 | 无权限 | FORBIDDEN |
| 404 | 资源不存在 | NOT_FOUND |
| 405 | 方法不允许 | METHOD_NOT_ALLOWED |
| 500 | 服务器内部错误 | INTERNAL_ERROR |
| 40001~49999 | 优惠券服务 | COUPON_NOT_FOUND |
| 50001~51999 | 商品服务 | PRODUCT_NOT_FOUND |
| 51001~51999 | 商品分类 | CATEGORY_NOT_FOUND |
| 52001~52999 | 对象存储 | OSS_FILE_TOO_LARGE |
| 60001~69999 | 订单服务 | ORDER_NOT_FOUND |
| 70001~79999 | 会员服务 | MEMBER_NOT_FOUND |
| 80001~89999 | 库存服务 | STOCK_NOT_ENOUGH |

> 新增错误码在 `ResultCode` 枚举追加，按码段归属模块，不使用裸数字 `R.error(500, "xxx")`。

---

## 五、日志

### 5.1 日志框架

统一使用 **SLF4J + Logback**（Spring Boot 默认），不直接使用 `System.out.println`。

```java
// ✅ 正确：用 Lombok 的 @Slf4j
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    public void createOrder(OrderCreateDTO dto) {
        log.info("创建订单: userId={}, amount={}", dto.getUserId(), dto.getAmount());
    }
}

// ❌ 错误
System.out.println("创建订单: " + dto);
```

### 5.2 日志级别

| 级别 | 使用场景 | 示例 |
|------|---------|------|
| ERROR | 系统异常、不可预期错误、需要人工介入 | DB 连接失败、第三方服务超时 |
| WARN | 业务异常、可恢复的异常、需要关注 | 库存不足、参数校验失败、重试 |
| INFO | 关键业务操作（创建、修改、删除、状态流转） | 创建订单、支付成功、取消订单 |
| DEBUG | 调试信息，生产关闭 | 方法入参出参、中间状态 |
| TRACE | 极细粒度追踪，生产关闭 | 框架内部流程 |

### 5.3 日志规范

| 规则 | 说明 |
|------|------|
| **用占位符，不用字符串拼接** | `log.info("订单: {}", orderId)` 而非 `log.info("订单: " + orderId)` |
| **不记录敏感信息** | 密码、token、身份证号不记录 |
| **异常对象作为最后一个参数** | `log.error("系统异常", e)` 而非 `log.error("系统异常: " + e.getMessage())` |
| **查询不记录 INFO** | 查询太频繁，日志量太大，只在 DEBUG 记录 |
| **关键操作记录上下文** | 创建订单时记录 userId、amount、skuId 等关键信息 |

```java
// ✅ 正确：占位符 + 异常对象单独传
log.error("创建订单失败: userId={}", dto.getUserId(), e);

// ❌ 错误：字符串拼接
log.error("创建订单失败: " + dto.getUserId() + ", " + e.getMessage());

// ❌ 错误：异常只取 message，丢失堆栈
log.error("创建订单失败: {}", e.getMessage());
```

### 5.4 各层日志策略

| 层 | 记录什么 | 级别 |
|----|---------|------|
| Controller | 写操作（创建/修改/删除） | INFO |
| Service | 关键业务决策、状态流转、远程调用结果 | INFO / WARN |
| Mapper | 不记录（MyBatis 自带 SQL 日志） | DEBUG |
| GlobalExceptionHandler | 业务异常 WARN，系统异常 ERROR | WARN / ERROR |

---

## 六、DTO / VO / Entity 规范

### 6.1 数据模型分类

| 类型 | 位置 | 作用 | 命名 |
|------|------|------|------|
| Entity | `entity/` | 与数据库表一一对应 | `{TableName}`（如 `Coupon`） |
| DTO | `dto/` | 接口入参（请求体） | `{Entity}{Action}DTO`（如 `CouponSaveDTO`） |
| VO | `vo/` | 接口出参（返回值） | `{Entity}VO`（如 `CouponVO`） |
| QueryDTO | `dto/` | 查询条件 | `{Entity}QueryDTO`（如 `CouponQueryDTO`） |

### 6.2 何时用 DTO / VO

| 场景 | 入参 | 出参 |
|------|------|------|
| 新增/修改 | DTO | `R<Void>` |
| 查询单个 | 路径参数 | `R<Entity>` 或 `R<VO>` |
| 查询列表 | QueryDTO | `R<Page<Entity>>` 或 `R<Page<VO>>` |
| 跨服务调用（Feign） | DTO | `R<DTO>` 或 `R<Entity>` |

**简单原则**：
- 查询可以直接返回 Entity（字段不多、没有敏感字段时）
- 写入（新增/修改）必须用 DTO，不能直接接收 Entity
- 返回值字段与 Entity 差异较大时用 VO（如需要拼接关联数据）

### 6.3 DTO 示例

```java
package com.mymall.coupon.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 优惠券保存 DTO（新增 + 修改共用，用校验分组区分）
 */
@Data
public class CouponSaveDTO {

    @NotNull(groups = Update.class, message = "修改时 id 不能为空")
    private Long id;

    @NotBlank(groups = {Create.class, Update.class}, message = "优惠券名称不能为空")
    @Length(max = 100, message = "优惠券名称最长 100 字符")
    private String couponName;

    @NotNull(groups = {Create.class, Update.class}, message = "优惠金额不能为空")
    @DecimalMin(value = "0.01", message = "优惠金额不能小于 0.01")
    private BigDecimal amount;

    @NotNull(groups = {Create.class, Update.class}, message = "使用门槛不能为空")
    @DecimalMin(value = "0", message = "使用门槛不能为负数")
    private BigDecimal minPoint;
}
```

### 6.4 Entity 继承 BaseEntity

所有 Entity **必须继承 `BaseEntity`**，复用 `id`、`createTime`、`updateTime` 字段：

```java
package com.mymall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sms_coupon")
public class Coupon extends BaseEntity {

    private String couponName;
    private BigDecimal amount;
    private BigDecimal minPoint;
    private Integer status;
}
```

> **注意**：所有实体统一继承 `BaseEntity`（含主键 id、审计字段 createBy/updateBy、逻辑删除 isDeleted、乐观锁 version）。建表 DDL 须有对应列，规范见 [表设计规范](./table-design-specification.md)。

### 6.5 Entity / DTO 转换

简单转换用 `BeanUtils.copyProperties`，复杂转换用 MapStruct（后期引入）：

```java
// Service 中转换
public void save(CouponSaveDTO dto) {
    Coupon coupon = new Coupon();
    BeanUtils.copyProperties(dto, coupon);
    this.save(coupon);
}
```

---

## 七、命名规范

### 7.1 项目级命名

| 元素 | 规范 | 示例 |
|------|------|------|
| 包名 | 全小写，单数 | `com.mymall.coupon.controller` |
| 类名 | 大驼峰 | `CouponController`、`OrderServiceImpl` |
| 方法名 | 小驼峰 | `createOrder`、`getById` |
| 变量名 | 小驼峰 | `couponName`、`orderList` |
| 常量名 | 全大写下划线 | `MAX_PAGE_SIZE`、`COUPON_STATUS_USED` |
| URL 路径 | 全小写中划线 | `/coupon/coupon-history` |
| 数据库表 | 全小写下划线 | `sms_coupon`、`ums_member` |
| 数据库字段 | 全小写下划线 | `coupon_name`、`create_time` |

### 7.2 类命名后缀约定

| 类型 | 后缀 | 示例 |
|------|------|------|
| Controller | `Controller` | `CouponController` |
| Service 接口 | `I{Entity}Service` | `ICouponService` |
| Service 实现 | `{Entity}ServiceImpl` | `CouponServiceImpl` |
| Mapper | `{Entity}Mapper` | `CouponMapper` |
| Entity | `{Entity}` | `Coupon` |
| DTO | `{Entity}{Action}DTO` | `CouponSaveDTO` |
| VO | `{Entity}VO` | `CouponVO` |
| Feign Client | `{Target}FeignClient` | `CouponFeignClient` |
| Config | `{Name}Config` | `MyBatisConfig` |
| Exception | `{Name}Exception` | `BizException` |
| Handler | `{Name}Handler` | `GlobalExceptionHandler` |

### 7.3 方法命名约定

| 操作 | 命名 | 示例 |
|------|------|------|
| 查询单个 | `get` / `find` | `getById`, `findByCouponName` |
| 查询列表 | `list` | `listByStatus` |
| 分页查询 | `page` | `pageQuery` |
| 新增 | `save` / `create` | `save`, `createOrder` |
| 修改 | `update` | `updateStatus` |
| 删除 | `remove` / `delete` | `removeById` |
| 批量 | `batch` 前缀 | `batchRemove` |
| 判断 | `is` / `has` / `exists` | `isActive`, `hasStock` |
| 统计 | `count` | `countByStatus` |

---

## 八、检查清单

开发完一个功能，提交前对照清单：

### Service 层
- [ ] 接口和实现分离？接口以 `I` 开头？
- [ ] `@Transactional` 加在 Service 实现方法上？指定了 `rollbackFor = Exception.class`？
- [ ] 事务方法没有同类自调用？
- [ ] 远程调用考虑了超时和降级？
- [ ] 构造器注入 + `@RequiredArgsConstructor`？

### 异常处理
- [ ] 业务规则不满足时抛 `BizException`？
- [ ] 没有在 Controller 里 try-catch？
- [ ] 没有把异常信息直接暴露给前端（系统异常返回"系统繁忙"）？

### 日志
- [ ] 关键写操作有 INFO 日志？
- [ ] 日志用占位符 `{}`，不用字符串拼接？
- [ ] 异常日志 `log.error("...", e)` 保留了完整堆栈？
- [ ] 没有记录敏感信息（密码、token）？

### DTO / VO / Entity
- [ ] 写入接口用了 DTO 而非直接接收 Entity？
- [ ] DTO 校验规则用 Jakarta Validation 注解声明？
- [ ] 新增/修改用了校验分组？
- [ ] Entity 继承了 BaseEntity？

### 命名
- [ ] 类名、方法名、变量名符合命名规范？
- [ ] URL 路径全小写中划线？
- [ ] 常量全大写下划线？
