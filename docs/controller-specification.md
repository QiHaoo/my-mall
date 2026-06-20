# Controller 接口编写规范

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
public R save(@Valid @RequestBody CouponSaveDTO dto) {
    couponService.save(dto);
    return R.ok();
}

// ❌ 错误：业务逻辑在 Controller
@PostMapping
public R save(@Valid @RequestBody CouponSaveDTO dto) {
    if (dto.getAmount().compareTo(new BigDecimal("0.01")) < 0) {  // 业务判断
        return R.error("金额不能小于0.01");
    }
    couponService.save(dto);
    return R.ok();
}
```

### 1.2 参数校验用 JSR 303

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
public R save(@Valid @RequestBody CouponSaveDTO dto) { ... }
```

> **校验异常统一处理**：`MethodArgumentNotValidException` 由全局异常处理器（后续补充 `mall-common` 的 `GlobalExceptionHandler`）统一捕获并返回 `R.error()`，Controller 中不需要写 `BindingResult` 判断。

---

## 二、返回值

### 2.1 统一使用 R

```java
// 无返回数据
return R.ok();

// 单个对象
return R.ok(coupon);

// 多个键值对
return R.ok().put("coupons", list).put("total", list.size());

// 分页（直接传 Page 对象）
return R.ok(page);
```

### 2.2 不手动 try-catch

Controller 不需要 try-catch 包装，异常统一由 `GlobalExceptionHandler` 处理。

```java
// ✅ 正确
@GetMapping("/{id}")
public R getById(@PathVariable Long id) {
    return R.ok(couponService.getById(id)); // 如果 Service 抛异常，全局处理器兜底
}

// ❌ 多余
@GetMapping("/{id}")
public R getById(@PathVariable Long id) {
    try {
        return R.ok(couponService.getById(id));
    } catch (Exception e) {
        return R.error(e.getMessage());
    }
}
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

- 全小写，单词间用 `-` 分隔（Spring 会自动映射为驼峰参数）
- 资源名用**复数**（`/coupons`），如果只用单数（`/coupon`）则整个项目统一
- 本项目统一使用**单数**（`/coupon`、`/member`、`/order`）

---

## 四、注解使用

### 4.1 类级别

```java
@RestController                    // = @Controller + @ResponseBody
@RequestMapping("/coupon")         // 统一路径前缀
@RequiredArgsConstructor           // 构造器注入
@Tag(name = "优惠券管理")           // SpringDoc 分组
public class CouponController {
```

### 4.2 方法级别

```java
@Operation(summary = "获取优惠券列表")     // SpringDoc 接口说明
@GetMapping("/list")
public R list() { ... }

@Operation(summary = "创建优惠券")
@PostMapping
public R save(@Valid @RequestBody CouponSaveDTO dto) { ... }
```

> **DTO 与 Entity 分离**：Controller 的入参/返回值使用 DTO，不要直接暴露数据库 Entity。DTO 放在 `dto/` 包下（如 `com.mymall.coupon.dto.CouponSaveDTO`）。

---

## 五、依赖注入

### 5.1 用构造器注入 + Lombok

```java
@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor       // 生成 final 字段的构造器
public class CouponController {

    private final ICouponService couponService;
    private final IWareFeignClient wareFeignClient;

    // 不需要手动写构造器，@RequiredArgsConstructor 自动生成
}
```

> 不用 `@Autowired` 字段注入，原因：无法将字段声明为 `final`；不利于单测手动传参；隐藏了类的依赖数量。

---

## 六、示例：完整 Controller

```java
package com.mymall.coupon.controller;

import com.mymall.common.result.R;
import com.mymall.coupon.dto.CouponSaveDTO;
import com.mymall.coupon.dto.CouponQueryDTO;
import com.mymall.coupon.service.ICouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券管理接口
 *
 * <p>只做参数校验和结果封装，业务逻辑全在 Service 层。
 */
@RestController
@RequestMapping("/coupon/coupon")
@RequiredArgsConstructor
@Tag(name = "优惠券管理")
public class CouponController {

    private final ICouponService couponService;

    @Operation(summary = "分页查询优惠券")
    @GetMapping
    public R list(@Valid CouponQueryDTO query) {
        return R.ok(couponService.pageQuery(query));
    }

    @Operation(summary = "优惠券详情")
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.ok(couponService.getById(id));
    }

    @Operation(summary = "创建优惠券")
    @PostMapping
    public R save(@Valid @RequestBody CouponSaveDTO dto) {
        couponService.save(dto);
        return R.ok();
    }

    @Operation(summary = "修改优惠券")
    @PutMapping
    public R update(@Valid @RequestBody CouponSaveDTO dto) {
        couponService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除优惠券")
    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        couponService.removeById(id);
        return R.ok();
    }
}
```

---

## 七、检查清单

写 Controller 时自查：

- [ ] 方法里有没有 `if/else` 业务分支 → 有就该移到 Service
- [ ] 入参是否用 `@Valid` + DTO 声明校验 → 不要手动判空
- [ ] 返回值是否用 `R` 包装 → 不要返回裸对象
- [ ] 有没有 `try-catch` → 全局异常处理器统一兜底
- [ ] URL 是否 RESTful → 非 CRUD 用动词
- [ ] 依赖注入是否用构造器 + `@RequiredArgsConstructor`
- [ ] 有没有直接暴露 Entity → 用 DTO
