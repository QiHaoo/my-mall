# 请求参数基础设施

> 涉及组件：`PageQuery`、`Create`、`Update`
>
> 这两个组件解决的是"请求参数"层面的问题：`PageQuery` 统一分页参数，`Create`/`Update` 实现同一 DTO 的分组校验。

## PageQuery：分页查询基类

### 问题背景

没有 PageQuery 之前，每个分页接口各自定义分页参数：

```java
// Controller A
@GetMapping("/brands/page")
public R page(@RequestParam Integer page, @RequestParam Integer size) { ... }

// Controller B
@GetMapping("/coupons/page")
public R page(@RequestParam Integer pageNum, @RequestParam Integer pageSize) { ... }

// Controller C
@GetMapping("/products/page")
public R page(@RequestParam Integer current, @RequestParam Integer limit) { ... }
```

问题：
1. 参数名不统一（page/size、pageNum/pageSize、current/limit）
2. 校验逻辑重复（每个接口都要判断页码 ≥ 1）
3. 默认值不统一（有的默认 10 条，有的默认 20 条）
4. 上限不统一（有的允许 pageSize=10000，直接拖垮 DB）

### PageQuery 的设计

```java
@Data
@Schema(description = "分页查询参数")
public class PageQuery {

    @Schema(description = "页码（从 1 开始）", defaultValue = "1", example = "1")
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    @Schema(description = "每页数量", defaultValue = "10", example = "10")
    @Min(value = 1, message = "每页数量最小为 1")
    @Max(value = 500, message = "每页数量最大为 500")
    private Integer pageSize = 10;
}
```

### 使用方式

```java
// 1. 业务查询 DTO 继承 PageQuery
@Data
public class BrandPageQuery extends PageQuery {
    private String name;        // 品牌名（模糊查询）
    private Integer status;     // 状态
}

// 2. Controller 直接接收
@GetMapping("/page")
public R<IPage<Brand>> page(@Valid BrandPageQuery query) {
    // query.getPageNum() / query.getPageSize() 已有默认值 + 校验
    return R.ok(brandService.page(query));
}
```

### 为什么上限是 500

```java
@Max(value = 500, message = "每页数量最大为 500")
```

这个 500 不是拍脑袋定的，它和 `MybatisPlusConfig` 里的分页拦截器上限对齐：

```java
// MybatisPlusConfig.java
private static final long MAX_PAGE_LIMIT = 500L;
pagination.setMaxLimit(MAX_PAGE_LIMIT);
```

双重防护：
- **第一层**：`@Max(500)` 在参数校验阶段拦截，返回 400 错误
- **第二层**：分页拦截器的 `maxLimit` 在 SQL 执行阶段兜底，即使绕过校验也不会查出超过 500 条

### 业界分页参数命名对比

| 框架 | 页码 | 每页数量 |
|------|------|---------|
| MyBatis-Plus | `current` | `size` |
| PageHelper | `pageNum` | `pageSize` |
| Spring Data | `page`（从 0 开始） | `size` |
| 本项目 | `pageNum` | `pageSize` |

本项目选 `pageNum`/`pageSize`，和 PageHelper 命名一致，也是国内最常用的命名。注意 `pageNum` 从 1 开始（不是 Spring Data 的从 0 开始），更符合直觉。

## Create / Update：校验分组

### 问题背景

同一个 DTO 在新增和修改时，校验规则不同：

```java
@Data
public class BrandDTO {
    private Long id;          // 新增时不需要，修改时必填
    private String name;      // 都必填
    private String logo;      // 都必填
}
```

如果用同一个校验组，无法区分 `id` 在新增时可选、修改时必填。

### 业界方案对比

| 方案 | 做法 | 优缺点 |
|------|------|--------|
| 校验分组（本项目） | `@NotNull(groups = Update.class)` | 一个 DTO 复用，Spring 原生支持 |
| 拆两个 DTO | `BrandCreateDTO` + `BrandUpdateDTO` | 各自独立，但有大量重复字段 |
| DTO 继承 | `BrandUpdateDTO extends BrandCreateDTO` + 加 id 字段 | 减少重复，但继承关系不自然 |
| 在 Service 里手动校验 | `if (id == null) throw ...` | 不声明式，容易遗漏 |

本项目选择校验分组，Spring 原生支持，一个 DTO 搞定。

### Create / Update 标记接口

```java
// 标记接口，无任何方法
public interface Create {}
public interface Update {}
```

标记接口（Marker Interface）的作用：本身不含逻辑，仅作为 `@Validated` 的分组标记，告诉 Spring "这次校验用哪组规则"。

### 使用方式

```java
// 1. DTO 上标注分组
@Data
public class BrandDTO {
    @Null(groups = Create.class)           // 新增时必须为 null
    @NotNull(groups = Update.class)         // 修改时必须非 null
    private Long id;

    @NotBlank(groups = {Create.class, Update.class})  // 两种场景都必填
    private String name;
}

// 2. Controller 上指定校验组
@PostMapping
public R create(@Validated(Create.class) @RequestBody BrandDTO dto) { ... }

@PutMapping
public R update(@Validated(Update.class) @RequestBody BrandDTO dto) { ... }
```

### `@Validated` vs `@Valid`

| 注解 | 来源 | 支持分组 | 使用位置 |
|------|------|---------|---------|
| `@Valid` | JSR 303 标准 | 不支持 | 方法参数、嵌套校验 |
| `@Validated` | Spring 扩展 | 支持 | 类、方法参数 |

分组校验必须用 `@Validated`。嵌套校验（DTO 里有 DTO 字段）用 `@Valid`。

### 校验异常如何被处理

当 `@Validated(Create.class)` 校验失败时，Spring 抛出 `MethodArgumentNotValidException`，被 [GlobalExceptionHandler](./02-response-exception.md) 捕获后转成 `R.error(400, "字段名: 错误信息")`。

## 设计取舍总结

| 决策 | 选择 | 理由 |
|------|------|------|
| 分页基类 | PageQuery 继承复用 | 统一参数命名、默认值、校验、上限 |
| pageSize 上限 | 500，与分页拦截器对齐 | 双重防护，防恶意大分页拖垮 DB |
| 校验分组 | 标记接口 + @Validated | 一个 DTO 复用，Spring 原生支持 |
| 校验注解 | JSR 380（jakarta.validation） | 标准化，不绑定具体实现 |
