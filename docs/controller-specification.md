# Controller 接口编写规范

> 对标生产级 RESTful API 规范，所学内容可直接迁移到生产环境。

---

## 一、核心原则

### 1.1 Controller 只做三件事

```
请求进来 → ① 参数校验 → ② 调用 Service → ③ 组装返回值
```

Controller **不写业务逻辑**。判断标准：如果你的 Controller 方法里有 `if/else` 分支判断业务状态、有 `for` 循环处理数据，说明业务逻辑泄漏到了 Controller 层，应该下沉到 Service。

```java
// ✅ 正确：干净透传
@PostMapping
public R<Void> save(@Valid @RequestBody CouponSaveDTO dto) {
    couponService.save(dto);
    return R.ok();
}

// ❌ 错误：业务逻辑在 Controller
@PostMapping
public R<Void> save(@Valid @RequestBody CouponSaveDTO dto) {
    if (dto.getAmount().compareTo(new BigDecimal("0.01")) < 0) {  // 业务判断
        return R.error("金额不能小于0.01");
    }
    couponService.save(dto);
    return R.ok();
}
```

### 1.2 参数校验用 Jakarta Validation

不为空、范围、格式等校验用注解声明在 DTO 上，Controller 只加 `@Valid` 触发。

```java
// DTO 上声明校验规则
@Data
public class CouponSaveDTO {
    @NotBlank(message = "优惠券名称不能为空")
    private String couponName;

    @NotNull @DecimalMin("0.01")
    private BigDecimal amount;
}

// Controller 只加 @Valid
@PostMapping
public R<Void> save(@Valid @RequestBody CouponSaveDTO dto) { ... }
```

> **校验异常统一处理**：`MethodArgumentNotValidException` 由 `GlobalExceptionHandler` 统一捕获并返回 `R.error()`，Controller 中不需要写 `BindingResult` 判断。详见 [编码规范 - 异常处理](./coding-standards.md#四异常处理)。

### 1.3 校验分组：新增 vs 修改

新增和修改的校验规则不同（如修改时需要 `id`），用**校验分组**区分：

```java
// 1. 定义分组标记接口
public interface Create {}
public interface Update {}

// 2. DTO 上指定分组
@Data
public class CouponSaveDTO {
    @NotNull(groups = Update.class)           // 修改时必须有 id
    private Long id;

    @NotBlank(groups = {Create.class, Update.class})  // 新增和修改都校验
    private String couponName;
}

// 3. Controller 上指定分组
@PostMapping
public R<Void> save(@Validated(Create.class) @RequestBody CouponSaveDTO dto) { ... }

@PutMapping
public R<Void> update(@Validated(Update.class) @RequestBody CouponSaveDTO dto) { ... }
```

---

## 二、返回值与 HTTP 状态码

### 2.1 统一使用 R

本项目采用**统一响应体**模式：HTTP 状态码始终返回 200，业务状态通过 `R.code` 表达。

```java
// 无返回数据
return R.ok();                              // R<Void>

// 单个对象
return R.ok(coupon);                        // R<Coupon>

// 多个键值对
return R.ok().put("coupons", list).put("total", list.size());  // R<Map<String, Object>>

// 分页
return R.ok(page);                          // R<Page<T>>
```

> **为什么不用标准 HTTP 状态码区分业务错误？** 电商系统业务错误种类多（库存不足、优惠券过期、订单状态不允许等），用 4xx/5xx 表达不够灵活，且网关/CDN 会拦截 4xx/5xx 导致前端拿不到响应体。统一 200 + 业务码是国内主流电商（淘宝、京东）的通行做法。

### 2.2 不手动 try-catch

Controller 不需要 try-catch 包装，异常统一由 `GlobalExceptionHandler` 处理。

```java
// ✅ 正确
@GetMapping("/{id}")
public R<Coupon> getById(@PathVariable Long id) {
    return R.ok(couponService.getById(id)); // 如果 Service 抛异常，全局处理器兜底
}

// ❌ 多余
@GetMapping("/{id}")
public R<Coupon> getById(@PathVariable Long id) {
    try {
        return R.ok(couponService.getById(id));
    } catch (Exception e) {
        return R.error(e.getMessage());
    }
}
```

### 2.3 泛型必须指定

所有 `R` 的使用必须指定泛型参数，不允许使用原始类型 `R`：

```java
// ✅ 正确
public R<Coupon> getById(@PathVariable Long id) { ... }
public R<Void> save(@Valid @RequestBody CouponSaveDTO dto) { ... }
public R<Map<String, Object>> list() { ... }

// ❌ 错误：原始类型，编译器告警
public R getById(@PathVariable Long id) { ... }
public R save(@Valid @RequestBody CouponSaveDTO dto) { ... }
```

---

## 三、URL 设计

### 3.0 路径必须以模块名开头

每个服务模块下所有 Controller 的路径，**第一段必须是模块名**，用于标识接口归属：

```
mall-coupon  → /coupon/xxx    ✅
mall-member  → /member/xxx    ✅
mall-order   → /order/xxx     ✅
```

同一模块下有多个 Controller 时，第二段区分资源：

```
CouponController        → /coupon/coupon
CouponHistoryController → /coupon/coupon-history
MemberController        → /member/member
AddressController       → /member/address
```

### 3.1 CRUD 接口用 RESTful

| 操作 | 方法 | 路径 | 示例 |
|------|------|------|------|
| 列表 | GET | `/{模块}/{资源}` | `GET /coupon/coupon` |
| 详情 | GET | `/{模块}/{资源}/{id}` | `GET /coupon/coupon/1` |
| 新增 | POST | `/{模块}/{资源}` | `POST /coupon/coupon` |
| 修改 | PUT | `/{模块}/{资源}` | `PUT /coupon/coupon` |
| 删除 | DELETE | `/{模块}/{资源}/{id}` | `DELETE /coupon/coupon/1` |

### 3.2 非 CRUD 操作用动词

非标准 CRUD 操作，路径末尾加动词，让调用方一眼看懂意图：

| 操作 | 方法 | 路径 |
|------|------|------|
| 发放优惠券 | POST | `/coupon/coupon/issue` |
| 领取优惠券 | POST | `/coupon/coupon/{id}/claim` |
| 锁定库存 | PUT | `/ware/ware/lock` |
| 取消订单 | PUT | `/order/order/{id}/cancel` |
| 提交订单 | POST | `/order/order/submit` |

### 3.3 路径命名

- 全小写，单词间用 `-` 分隔
- 资源名用**单数**，本项目统一（`/coupon`、`/member`、`/order`）
- 动词用小写驼峰或 `-` 分隔，本项目统一用 `-`（`/cancel`、`/batch-delete`）

### 3.4 批量操作

批量操作路径加 `batch-` 前缀，用 POST + 请求体传 ID 列表：

| 操作 | 方法 | 路径 | 请求体 |
|------|------|------|--------|
| 批量删除 | POST | `/coupon/coupon/batch-delete` | `{"ids": [1, 2, 3]}` |
| 批量上架 | POST | `/product/spu/batch-up` | `{"ids": [1, 2, 3]}` |

> 批量删除不用 `DELETE` + body，因为部分 HTTP 客户端/网关不支持 DELETE 请求体。用 POST 更通用。

---

## 四、分页查询

### 4.1 请求参数

分页查询统一用 **Query 参数**（非 JSON Body），方便拼接 URL 和缓存：

```
GET /coupon/coupon?pageNum=1&pageSize=10&status=1&keyword=满减
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `pageNum` | Integer | 1 | 页码，从 1 开始 |
| `pageSize` | Integer | 10 | 每页条数，最大 100 |
| 其他 | — | — | 业务筛选条件 |

### 4.2 查询 DTO

查询条件封装为 QueryDTO，用 `@ModelAttribute` 自动绑定：

```java
@Data
public class CouponQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private Integer status;          // 可选筛选
    private String keyword;          // 可选关键词
}
```

```java
@GetMapping
public R<Page<Coupon>> list(CouponQueryDTO query) {
    return R.ok(couponService.pageQuery(query));
}
```

### 4.3 分页响应

使用 MyBatis-Plus 的 `Page<T>` 作为返回数据，序列化后结构：

```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "records": [...],
        "total": 100,
        "size": 10,
        "current": 1,
        "pages": 10
    }
}
```

---

## 五、注解使用

### 5.1 类级别

```java
@RestController                    // = @Controller + @ResponseBody
@RequestMapping("/coupon/coupon")  // 模块名 + 资源名
@RequiredArgsConstructor           // 构造器注入
@Tag(name = "优惠券管理")           // SpringDoc OpenAPI 分组
public class CouponController {
```

### 5.2 方法级别

```java
@Operation(summary = "获取优惠券列表")     // SpringDoc 接口说明
@GetMapping
public R<Page<Coupon>> list(CouponQueryDTO query) { ... }

@Operation(summary = "创建优惠券")
@PostMapping
public R<Void> save(@Validated(Create.class) @RequestBody CouponSaveDTO dto) { ... }
```

### 5.3 SpringDoc OpenAPI 注解

生产项目 API 文档是标配，所有 Controller 必须加 SpringDoc 注解：

| 注解 | 位置 | 作用 |
|------|------|------|
| `@Tag(name = "...")` | 类 | 接口分组 |
| `@Operation(summary = "...")` | 方法 | 接口说明 |
| `@Parameter(description = "...")` | 参数 | 参数说明 |

---

## 六、依赖注入

### 6.1 用构造器注入 + Lombok

```java
@RestController
@RequestMapping("/coupon/coupon")
@RequiredArgsConstructor       // 生成 final 字段的构造器
public class CouponController {

    private final ICouponService couponService;
    private final CouponFeignClient couponFeignClient;

    // 不需要手动写构造器，@RequiredArgsConstructor 自动生成
}
```

> 不用 `@Autowired` 字段注入，原因：无法将字段声明为 `final`；不利于单测手动传参；隐藏了类的依赖数量。详见 [编码规范 - 可测试性设计](./coding-standards.md#二可测试性设计原则)。

---

## 七、日志

### 7.1 Controller 层日志规范

Controller 层**只记录关键操作**（创建、修改、删除），不记录查询。使用 `@Slf4j` 注解。

```java
@Slf4j
@RestController
public class CouponController {

    @PostMapping
    public R<Void> save(@Validated(Create.class) @RequestBody CouponSaveDTO dto) {
        log.info("创建优惠券: couponName={}", dto.getCouponName());
        couponService.save(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        log.info("删除优惠券: id={}", id);
        couponService.removeById(id);
        return R.ok();
    }
}
```

| 场景 | 日志级别 | 示例 |
|------|---------|------|
| 创建/修改/删除 | INFO | `log.info("创建优惠券: name={}", name)` |
| 参数异常 | WARN | 由 GlobalExceptionHandler 记录 |
| 系统异常 | ERROR | 由 GlobalExceptionHandler 记录 |
| 查询操作 | 不记录 | 查询太频繁，日志量太大 |

> **日志规范详见** [编码规范 - 日志](./coding-standards.md#五日志)。

---

## 八、幂等性

### 8.1 需要幂等的场景

| 场景 | 原因 | 方案 |
|------|------|------|
| 创建订单 | 网络重试导致重复下单 | 前端传 `clientToken`，后端用 Redis SETNX 去重 |
| 支付回调 | MQ 重复消费 | 状态机校验（已支付不再处理） |
| 优惠券领取 | 用户快速点击 | 数据库唯一约束（user_id + coupon_id） |

### 8.2 幂等 Token 实现

```java
@PostMapping("/submit")
public R<Long> submit(
        @RequestHeader("X-Idempotent-Token") String token,  // 前端生成的唯一 token
        @Valid @RequestBody OrderSubmitDTO dto) {
    return R.ok(orderService.submit(dto, token));
}
```

```java
// Service 中用 Redis SETNX 保证幂等
public Long submit(OrderSubmitDTO dto, String token) {
    // token 存在则说明已处理过，直接返回已有结果
    Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent("idempotent:order:" + token, "1", 24, TimeUnit.HOURS);
    if (Boolean.FALSE.equals(acquired)) {
        throw new BizException("请勿重复提交");
    }
    // ... 创建订单
}
```

---

## 九、示例：完整 Controller

```java
package com.mymall.coupon.controller;

import com.mymall.common.result.R;
import com.mymall.coupon.dto.CouponSaveDTO;
import com.mymall.coupon.dto.CouponQueryDTO;
import com.mymall.coupon.entity.Coupon;
import com.mymall.coupon.service.ICouponService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券管理接口
 *
 * <p>只做参数校验和结果封装，业务逻辑全在 Service 层。
 */
@Slf4j
@RestController
@RequestMapping("/coupon/coupon")
@RequiredArgsConstructor
@Tag(name = "优惠券管理")
public class CouponController {

    private final ICouponService couponService;

    @Operation(summary = "分页查询优惠券")
    @GetMapping
    public R<Page<Coupon>> list(CouponQueryDTO query) {
        return R.ok(couponService.pageQuery(query));
    }

    @Operation(summary = "优惠券详情")
    @GetMapping("/{id}")
    public R<Coupon> getById(@PathVariable Long id) {
        return R.ok(couponService.getById(id));
    }

    @Operation(summary = "创建优惠券")
    @PostMapping
    public R<Void> save(@Validated(Create.class) @RequestBody CouponSaveDTO dto) {
        log.info("创建优惠券: couponName={}", dto.getCouponName());
        couponService.save(dto);
        return R.ok();
    }

    @Operation(summary = "修改优惠券")
    @PutMapping
    public R<Void> update(@Validated(Update.class) @RequestBody CouponSaveDTO dto) {
        log.info("修改优惠券: id={}", dto.getId());
        couponService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除优惠券")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        log.info("删除优惠券: id={}", id);
        couponService.removeById(id);
        return R.ok();
    }
}
```

---

## 十、检查清单

写 Controller 时自查：

- [ ] 方法里有没有 `if/else` 业务分支 → 有就该移到 Service
- [ ] 入参是否用 `@Valid` / `@Validated` + DTO 声明校验 → 不要手动判空
- [ ] 新增/修改是否用了校验分组 → `@Validated(Create.class)` / `@Validated(Update.class)`
- [ ] 返回值是否用 `R<T>` 包装并指定泛型 → 不要返回裸对象或原始类型 `R`
- [ ] 有没有 `try-catch` → 全局异常处理器统一兜底
- [ ] URL 是否以模块名开头 → `/coupon/coupon`、`/member/member`
- [ ] URL 是否 RESTful → 非 CRUD 用动词，批量加 `batch-` 前缀
- [ ] 依赖注入是否用构造器 + `@RequiredArgsConstructor`
- [ ] 有没有直接暴露 Entity → 查询可以返回 Entity/VO，写入用 DTO
- [ ] 有没有加 SpringDoc 注解 → `@Tag` + `@Operation`
- [ ] 写操作有没有日志 → `log.info("创建xxx: key={}", value)`
- [ ] 创建/支付等关键操作是否考虑了幂等性
