# 品牌管理 - 需求与接口文档

> 模块：`mall-product`
> 表：`pms_brand`、`pms_category_brand_relation`
> 版本：v1.0
> 更新时间：2026-06-24

---

## 一、业务背景

品牌是商品的核心基础数据之一，挂在 SPU 上（`pms_spu_info.brand_id`）。一个品牌可隶属于多个商品分类（如"小米"既属于"手机"也属于"家电"），因此品牌与分类是**多对多**关系，通过 `pms_category_brand_relation` 维护。

品牌数据被以下模块引用：

| 引用模块 | 表 | 字段 | 说明 |
|---------|---|------|------|
| 商品 | `pms_spu_info` | `brand_id` | SPU 归属品牌 |
| 商品分类 | `pms_category_brand_relation` | `brand_id` | 品牌-分类关联 |
| 检索 | `pms_category_brand_relation` | `catelog_id` | 前台按分类筛品牌 |

品牌的变更（删除、改名）必须评估对以上模块的影响：
- 删除品牌前检查是否有 SPU 引用
- 改名时同步刷新 `pms_category_brand_relation.brand_name` 冗余字段

> **Logo 图片**：前端通过 `mall-oss` 拿到 Presigned URL 直传 MinIO 后，将返回的对象访问 URL 作为 `logo` 字段提交给品牌接口。品牌服务**不接收文件流**，只存储 URL。详见 [对象存储服务设计](./object-storage-design.md)。

---

## 二、功能需求

### 2.1 功能清单

| 编号 | 功能 | 说明 |
|------|------|------|
| F1 | 分页查询品牌 | 支持按品牌名模糊、首字母、显示状态筛选，分页返回 |
| F2 | 品牌详情 | 按 ID 查询品牌，含关联分类列表 |
| F3 | 新增品牌 | 含 logo URL，可选关联多个分类 |
| F4 | 修改品牌 | 修改基础信息，同步更新关联分类与冗余品牌名 |
| F5 | 更新显示状态 | 单独切换显示/隐藏（`show_status`） |
| F6 | 删除品牌 | 逻辑删除，先校验 SPU 引用 |
| F7 | 查询分类下的品牌 | 前台检索用，按三级分类 ID 返回品牌列表 |

### 2.2 业务规则

#### F1 分页查询品牌

- 仅返回 `is_deleted = 0` 的品牌（MyBatis-Plus `@TableLogic` 自动过滤）
- 按 `sort` 升序 → `id` 升序排列
- `name` 模糊匹配（`LIKE %name%`）
- `firstLetter` 精确匹配，`showStatus` 精确匹配
- `pageNum` 默认 1，`pageSize` 默认 10、最大 100

#### F2 品牌详情

- 返回品牌基础字段 + `categoryIds`（关联的三级分类 ID 列表）
- 关联分类来自 `pms_category_brand_relation`，仅取 `is_deleted = 0`

#### F3 新增品牌

- 品牌名全局唯一（`pms_brand.uk_name`）
- `logo` 为 OSS 直传后返回的访问 URL，前端必传
- `showStatus` 默认 1（显示），可选传
- `firstLetter` 可选；传则必须为单个小写/大写字母，后端统一转大写存储
- `sort` 默认 0
- `categoryIds` 可选；传则逐个校验分类存在且为三级分类（`cat_level = 3`），写入 `pms_category_brand_relation`（冗余 `brand_name` / `catelog_name`）
- 审计字段（`create_by` / `update_by`）由 `MyMetaObjectHandler` 自动填充

#### F4 修改品牌

- `id` 必填，校验品牌存在
- 品牌名变更时校验全局唯一（排除自身）
- 品牌名变更时同步刷新 `pms_category_brand_relation.brand_name`
- `categoryIds` 变更时：先逻辑删除该品牌旧关联，再按新列表写入（全量覆盖）
- 乐观锁：更新携带 `version`，并发冲突抛异常（由 `OptimisticLockerInnerInterceptor` 处理）

#### F5 更新显示状态

- `showStatus` 仅允许 0 或 1
- 单字段更新，不影响其他字段、不更新关联分类
- 隐藏品牌后，前台分类筛选不再展示该品牌

#### F6 删除品牌

- **引用检查**：`pms_spu_info.brand_id` 存在引用则拒绝（含逻辑删除的 SPU 不计入）
- **逻辑删除**：`pms_brand.is_deleted` 置 1（`@TableLogic`）
- **关联清理**：同时逻辑删除 `pms_category_brand_relation` 中该品牌的全部关联记录
- 删除后品牌名 `uk_name` 唯一约束不再阻塞新建同名品牌（逻辑删除记录保留但 `is_deleted=1`；如需严格复用同名，建议为 `uk_name` 改为 `uk_name_del` 包含 `is_deleted`，见下文索引说明）

#### F7 查询分类下的品牌

- 路径参数为三级分类 ID
- 联表 `pms_category_brand_relation` 查出该分类下所有 `show_status = 1` 的品牌
- 仅返回前台展示需要的精简字段（id、name、logo、firstLetter）

---

## 三、数据模型

### 3.1 表结构 `pms_brand`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 主键（BaseEntity） |
| `name` | varchar(64) | 是 | 品牌名，全局唯一 |
| `logo` | varchar(1024) | 否 | 品牌 logo URL（OSS 直传后返回） |
| `descript` | text | 否 | 品牌介绍 |
| `show_status` | tinyint | 是 | 显示状态：0-不显示，1-显示，默认 1 |
| `first_letter` | varchar(1) | 否 | 检索首字母，统一大写存储 |
| `sort` | int | 是 | 排序值，越小越靠前，默认 0 |
| `create_time` / `update_time` | datetime | 是 | 审计字段（BaseEntity） |
| `create_by` / `update_by` | bigint | 否 | 审计字段（BaseEntity） |
| `is_deleted` | tinyint | 是 | 逻辑删除：0-正常，1-删除（`@TableLogic`） |
| `version` | int | 是 | 乐观锁版本号（`@Version`） |

### 3.2 表结构 `pms_category_brand_relation`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 主键 |
| `brand_id` | bigint | 是 | 品牌 ID |
| `catelog_id` | bigint | 是 | 分类 ID（三级） |
| `brand_name` | varchar(64) | 否 | 品牌名（冗余，便于关联查询展示） |
| `catelog_name` | varchar(64) | 否 | 分类名（冗余） |
| `create_time` / `update_time` | datetime | 是 | 审计字段 |
| `create_by` / `update_by` | bigint | 否 | 审计字段 |
| `is_deleted` | tinyint | 是 | 逻辑删除 |
| `version` | int | 是 | 乐观锁 |

> `brand_name` / `catelog_name` 为冗余字段：品牌/分类改名时需同步刷新，避免联表查询。

### 3.3 索引

`pms_brand`：

| 索引 | 字段 | 类型 | 说明 |
|------|------|------|------|
| PK | `id` | PRIMARY | 主键 |
| UK_NAME | `name` | UNIQUE | 品牌名全局唯一 |

`pms_category_brand_relation`（建表语句已包含）：

| 索引 | 字段 | 类型 | 说明 |
|------|------|------|------|
| PK | `id` | PRIMARY | 主键 |
| UK_BRAND_CATELOG | `brand_id, catelog_id` | UNIQUE | 防止重复关联 |
| IDX_CATELOG_ID | `catelog_id` | NORMAL | 按分类查品牌 |

> **生产环境建议（逻辑删除与唯一约束冲突）**：`uk_name` 在逻辑删除后仍会阻塞新建同名品牌。若业务允许复用同名，建议将唯一索引改为包含 `is_deleted`：
> ```sql
> ALTER TABLE pms_brand DROP INDEX uk_name;
> ALTER TABLE pms_brand ADD UNIQUE INDEX uk_name_del (name, is_deleted);
> ```
> 本设计文档默认保留 `uk_name`，同名品牌删除后不可重建（保守策略，避免历史品牌混淆）。

---

## 四、接口设计

### 4.1 接口总览

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 分页查询 | GET | `/product/brand` | 条件分页查询 |
| 品牌详情 | GET | `/product/brand/{id}` | 含关联分类 |
| 新增品牌 | POST | `/product/brand` | 含 logo URL + 关联分类 |
| 修改品牌 | PUT | `/product/brand` | 全量覆盖关联分类 |
| 更新显示状态 | PUT | `/product/brand/{id}/show-status` | 切换显示/隐藏 |
| 删除品牌 | DELETE | `/product/brand/{id}` | 逻辑删除 + 引用检查 |
| 分类下品牌 | GET | `/product/brand/by-category/{catelogId}` | 前台检索用 |

> 所有接口经过网关（`localhost:1000`），前端 `baseUrl` 为 `/api`，网关将 `/api/product/**` 路由到 `mall-product` 服务（`StripPrefix=1`）。

### 4.2 分页查询品牌

**GET** `/product/brand?pageNum=1&pageSize=10&name=小米&firstLetter=X&showStatus=1`

**请求参数**（Query，`BrandQueryDTO`）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pageNum` | Integer | 否 | 默认 1 |
| `pageSize` | Integer | 否 | 默认 10，最大 100 |
| `name` | String | 否 | 品牌名模糊匹配 |
| `firstLetter` | String | 否 | 首字母精确匹配 |
| `showStatus` | Integer | 否 | 0-隐藏，1-显示 |

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "records": [
            {
                "id": 1,
                "name": "小米",
                "logo": "https://oss.example.com/brand/mi.png",
                "descript": "为发烧而生",
                "showStatus": 1,
                "firstLetter": "X",
                "sort": 0
            }
        ],
        "total": 1,
        "size": 10,
        "current": 1,
        "pages": 1
    }
}
```

**实现要点**：
- `LambdaQueryWrapper<Brand>` 拼接条件，`like` / `eq` 按参数非空判断
- 排序：`order by sort asc, id asc`
- 分页用 MyBatis-Plus `Page<Brand>`，返回 `R<Page<BrandVO>>`

### 4.3 品牌详情

**GET** `/product/brand/{id}`

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "id": 1,
        "name": "小米",
        "logo": "https://oss.example.com/brand/mi.png",
        "descript": "为发烧而生",
        "showStatus": 1,
        "firstLetter": "X",
        "sort": 0,
        "categoryIds": [225, 226]
    }
}
```

**实现要点**：
- 查品牌主记录，不存在抛 `BRAND_NOT_FOUND`
- 查 `pms_category_brand_relation`（`brand_id = id` 且 `is_deleted = 0`）取 `catelog_id` 列表

### 4.4 新增品牌

**POST** `/product/brand`

**请求体**（`BrandSaveDTO`，校验分组 `Create.class`）：

```json
{
    "name": "小米",
    "logo": "https://oss.example.com/brand/mi.png",
    "descript": "为发烧而生",
    "showStatus": 1,
    "firstLetter": "X",
    "sort": 0,
    "categoryIds": [225, 226]
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `id` | Long | 修改时必填 | `@NotNull(groups = Update.class)` |
| `name` | String | 是 | `@NotBlank`，最大 64 字符 |
| `logo` | String | 是 | URL 格式，最大 1024 字符 |
| `descript` | String | 否 | 最大 500 字符 |
| `showStatus` | Integer | 否 | 0 或 1，默认 1 |
| `firstLetter` | String | 否 | 单个字母，后端转大写 |
| `sort` | Integer | 否 | `@Min(0)`，默认 0 |
| `categoryIds` | `List<Long>` | 否 | 每个需为存在的三级分类 |
| `version` | Integer | 修改时必填 | 乐观锁版本号 |

> 新增/修改复用同一个 `BrandSaveDTO`，通过 `@Validated(Create.class)` / `@Validated(Update.class)` 区分（见 [Controller 规范 - 校验分组](../standards/controller-specification.md)）。

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// 品牌名重复
{ "code": 53002, "msg": "品牌名已存在", "data": null }

// 关联分类不存在
{ "code": 53004, "msg": "关联分类 [999] 不存在或非三级分类", "data": null }
```

**业务逻辑**：

```
1. 校验品牌名唯一（排除逻辑删除），重复抛 BRAND_NAME_DUPLICATE
2. firstLetter 非空时校验为字母，统一转大写
3. showStatus 为空时默认 1
4. 插入 pms_brand（审计字段由 MyMetaObjectHandler 填充）
5. 若 categoryIds 非空：
   a. 批量查询 pms_category，校验全部存在且 cat_level = 3
   b. 组装 pms_category_brand_relation 记录（含 brand_name / catelog_name 冗余）
   c. 批量插入关联表
   （第 4、5 步在同一事务 @Transactional）
```

### 4.5 修改品牌

**PUT** `/product/brand`

**请求体**（`BrandSaveDTO`，校验分组 `Update.class`）：

```json
{
    "id": 1,
    "name": "小米",
    "logo": "https://oss.example.com/brand/mi-new.png",
    "descript": "为发烧而生",
    "showStatus": 1,
    "firstLetter": "X",
    "sort": 0,
    "categoryIds": [225],
    "version": 3
}
```

**响应**：

```json
{ "code": 200, "msg": "success", "data": {} }
```

**业务逻辑**：

```
1. 查询品牌，不存在抛 BRAND_NOT_FOUND
2. 品牌名变更时校验唯一（排除自身 + 逻辑删除）
3. MyBatis-Plus updateById（携带 version 触发乐观锁，冲突抛异常）
4. 品牌名变更 → UPDATE pms_category_brand_relation SET brand_name=? WHERE brand_id=?
5. categoryIds 变更（全量覆盖）：
   a. 逻辑删除该品牌全部旧关联（UPDATE ... SET is_deleted=1 WHERE brand_id=?）
   b. 校验新 categoryIds 全部为三级分类
   c. 批量插入新关联
   （全部在同一事务 @Transactional）
```

### 4.6 更新显示状态

**PUT** `/product/brand/{id}/show-status`

**请求体**：

```json
{
    "showStatus": 0
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `showStatus` | Integer | 是 | 仅允许 0 或 1 |

**响应**：

```json
{ "code": 200, "msg": "success", "data": {} }
```

**业务逻辑**：

```
1. 校验品牌存在
2. 校验 showStatus ∈ {0,1}，否则抛 BRAND_SHOW_STATUS_INVALID
3. UPDATE pms_brand SET show_status=? WHERE id=?（携带 version）
```

### 4.7 删除品牌

**DELETE** `/product/brand/{id}`

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// 有 SPU 引用，拒绝
{ "code": 53003, "msg": "品牌 [小米] 下存在关联商品，无法删除", "data": null }
```

**业务逻辑**：

```
1. 查询品牌，不存在抛 BRAND_NOT_FOUND
2. 引用检查：SELECT count(*) FROM pms_spu_info WHERE brand_id=? AND is_deleted=0
   a. > 0 → 抛 BRAND_HAS_PRODUCTS，返回品牌名
3. 逻辑删除品牌：UPDATE pms_brand SET is_deleted=1 WHERE id=?
4. 逻辑删除关联：UPDATE pms_category_brand_relation SET is_deleted=1 WHERE brand_id=?
   （第 3、4 步在同一事务 @Transactional）
```

### 4.8 查询分类下的品牌

**GET** `/product/brand/by-category/{catelogId}`

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": [
        { "id": 1, "name": "小米", "logo": "https://...", "firstLetter": "X" }
    ]
}
```

**业务逻辑**：

```
1. 联表查询：
   SELECT b.id, b.name, b.logo, b.first_letter
   FROM pms_brand b
   INNER JOIN pms_category_brand_relation r ON r.brand_id = b.id
   WHERE r.catelog_id = ? AND r.is_deleted = 0
     AND b.is_deleted = 0 AND b.show_status = 1
   ORDER BY b.sort ASC, b.id ASC
2. 返回精简 BrandVO 列表（前台展示用，不带 descript 等大字段）
```

---

## 五、DTO / VO 定义

```
com.mymall.product.dto.brand/
├── BrandSaveDTO.java          // 新增/修改（校验分组 Create/Update）
├── BrandQueryDTO.java         // 分页查询条件
├── BrandShowStatusDTO.java    // 更新显示状态
├── BrandVO.java               // 详情/列表返回
└── BrandSimpleVO.java         // 分类下品牌精简返回
```

### 5.1 BrandSaveDTO

```java
@Data
@Schema(description = "新增/修改品牌")
public class BrandSaveDTO {

    @NotNull(groups = Update.class, message = "品牌ID不能为空")
    @Schema(description = "品牌ID（修改时必填）")
    private Long id;

    @NotBlank(message = "品牌名不能为空")
    @Size(max = 64, message = "品牌名最长 64 字符")
    @Schema(description = "品牌名", example = "小米")
    private String name;

    @NotBlank(message = "logo地址不能为空")
    @Size(max = 1024, message = "logo地址最长 1024 字符")
    @Schema(description = "品牌logo URL（OSS直传后返回）")
    private String logo;

    @Size(max = 500, message = "品牌介绍最长 500 字符")
    @Schema(description = "品牌介绍")
    private String descript;

    @Schema(description = "显示状态：0-隐藏 1-显示", example = "1")
    private Integer showStatus;

    @Pattern(regexp = "^[A-Za-z]$", message = "首字母必须为单个字母")
    @Schema(description = "检索首字母（后端转大写存储）")
    private String firstLetter;

    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "关联三级分类ID列表")
    private List<Long> categoryIds;

    @NotNull(groups = Update.class, message = "版本号不能为空")
    @Schema(description = "乐观锁版本号（修改时必填）")
    private Integer version;
}
```

### 5.2 BrandQueryDTO

```java
@Data
@Schema(description = "品牌分页查询条件")
public class BrandQueryDTO {
    @Min(value = 1, message = "页码从 1 开始")
    private Integer pageNum = 1;

    @Min(value = 1)
    @Max(value = 100, message = "每页最多 100 条")
    private Integer pageSize = 10;

    @Schema(description = "品牌名模糊匹配")
    private String name;

    @Pattern(regexp = "^[A-Za-z]$", message = "首字母必须为单个字母")
    @Schema(description = "首字母精确匹配")
    private String firstLetter;

    @Schema(description = "显示状态：0-隐藏 1-显示")
    private Integer showStatus;
}
```

### 5.3 BrandShowStatusDTO

```java
@Data
@Schema(description = "更新品牌显示状态")
public class BrandShowStatusDTO {
    @NotNull(message = "显示状态不能为空")
    @Min(0) @Max(1)
    @Schema(description = "显示状态：0-隐藏 1-显示")
    private Integer showStatus;
}
```

### 5.4 BrandVO

```java
@Data
@Schema(description = "品牌详情/列表项")
public class BrandVO {
    private Long id;
    private String name;
    private String logo;
    private String descript;
    private Integer showStatus;
    private String firstLetter;
    private Integer sort;

    @Schema(description = "关联三级分类ID列表（详情接口返回）")
    private List<Long> categoryIds;
}
```

### 5.5 BrandSimpleVO

```java
@Data
@Schema(description = "分类下品牌（前台精简）")
public class BrandSimpleVO {
    private Long id;
    private String name;
    private String logo;
    private String firstLetter;
}
```

---

## 六、错误码

在 `ResultCode` 枚举中补充品牌相关错误码（**53001+ 码段**，紧接对象存储 52001~52007 之后）：

| 错误码 | 枚举值 | 消息 | 触发场景 |
|--------|--------|------|---------|
| 53001 | `BRAND_NOT_FOUND` | 品牌不存在 | ID 查询不到 |
| 53002 | `BRAND_NAME_DUPLICATE` | 品牌名已存在 | 新增/改名时名称重复 |
| 53003 | `BRAND_HAS_PRODUCTS` | 品牌下存在关联商品，无法删除 | 删除时 `pms_spu_info` 有引用 |
| 53004 | `BRAND_CATEGORY_INVALID` | 关联分类不存在或非三级分类 | `categoryIds` 校验失败 |
| 53005 | `BRAND_SHOW_STATUS_INVALID` | 显示状态值非法 | `showStatus` 非 0/1 |

对应枚举定义（追加到 `ResultCode`）：

```java
// ==================== 商品品牌 53001+ ====================
BRAND_NOT_FOUND(53001, "品牌不存在"),
BRAND_NAME_DUPLICATE(53002, "品牌名已存在"),
BRAND_HAS_PRODUCTS(53003, "品牌下存在关联商品，无法删除"),
BRAND_CATEGORY_INVALID(53004, "关联分类不存在或非三级分类"),
BRAND_SHOW_STATUS_INVALID(53005, "显示状态值非法"),
```

> 同时在 `ResultCode` 头部码段规划注释中补一行：`53001~53999 - 商品品牌`。

---

## 七、网关路由

前端管理后台通过网关访问品牌接口：

```
浏览器 → http://localhost:1000/api/product/brand?pageNum=1
                  │
                  ▼
         网关（mall-gateway:1000）
         路由：product-route
         Predicate: Path=/api/product/**
         Filter: StripPrefix=1
                  │
                  ▼
         lb://mall-product
         实际请求：/product/brand?pageNum=1
```

> 与分类接口共用 `product-route` 路由规则，无需额外配置。

---

## 八、HTTP 调试文件

`http/product-brand-demo.http`

```http
### 1. 分页查询品牌
GET http://localhost:1000/api/product/brand?pageNum=1&pageSize=10&name=小米

### 2. 品牌详情
GET http://localhost:1000/api/product/brand/1

### 3. 新增品牌（含关联分类）
POST http://localhost:1000/api/product/brand
Content-Type: application/json

{
    "name": "小米",
    "logo": "https://oss.example.com/brand/mi.png",
    "descript": "为发烧而生",
    "showStatus": 1,
    "firstLetter": "X",
    "sort": 0,
    "categoryIds": [225, 226]
}

### 4. 修改品牌（全量覆盖关联分类为 [225]）
PUT http://localhost:1000/api/product/brand
Content-Type: application/json

{
    "id": 1,
    "name": "小米",
    "logo": "https://oss.example.com/brand/mi-new.png",
    "showStatus": 1,
    "firstLetter": "X",
    "sort": 0,
    "categoryIds": [225],
    "version": 0
}

### 5. 更新显示状态（隐藏）
PUT http://localhost:1000/api/product/brand/1/show-status
Content-Type: application/json

{
    "showStatus": 0
}

### 6. 删除品牌
DELETE http://localhost:1000/api/product/brand/1

### 7. 查询分类下的品牌（前台检索）
GET http://localhost:1000/api/product/brand/by-category/225
```

---

## 九、非功能性要求

| 项目 | 要求 |
|------|------|
| 性能 | 分页查询 < 200ms（命中 `idx_catelog_id` / `uk_name`）；分类下品牌查询走关联表索引 |
| 并发 | 品牌为低频写操作，乐观锁（`@Version`）即可，无需分布式锁 |
| 缓存 | 品牌列表变更低频，可缓存分类下品牌到 Redis（TTL 30 分钟），写操作后主动失效 |
| 事务 | 新增/修改/删除涉及 `pms_brand` + `pms_category_brand_relation` 双表，必须 `@Transactional` |
| 安全 | 管理接口需管理员权限（网关 JWT 鉴权，待实现）；`by-category` 接口可匿名访问 |
| 日志 | 写操作记录操作人 + 变更内容（审计需要），查询不记录 |
| 幂等 | 新增靠 `uk_name` 唯一约束兜底重复提交；其余为覆盖写，天然幂等 |
| 一致性 | 品牌改名 / 分类改名需同步刷新 `pms_category_brand_relation` 冗余字段 |

---

## 十、前端设计

### 页面布局

> Figma 设计稿：[待产出]()
>
> 在 Figma 出稿前，以下列文字描述作为页面结构契约。

**品牌列表页**（管理后台 `/product/brand`）

- 顶部筛选区：品牌名输入框 + 首字母下拉（A-Z）+ 显示状态下拉（全部/显示/隐藏）+ 查询/重置按钮
- 操作区：新增品牌按钮 + 批量删除按钮
- 表格区：列含 logo 缩略图 / 品牌名 / 首字母 / 显示状态(switch) / 排序值 / 操作（编辑 / 删除）
- 底部分页区：el-pagination（pageNum / pageSize）

**品牌表单弹窗**（新增 / 编辑复用）

- 基础信息：品牌名 / logo 上传 / 首字母 / 排序值 / 显示状态
- 品牌介绍：textarea（最多 500 字符）
- 关联分类：el-cascader 多选（仅三级分类可选），已选分类以 tag 展示

### 组件拆分

| 组件 | 职责 | 消费接口 |
|------|------|---------|
| BrandList | 列表页主体：筛选 + 表格 + 分页 | GET /product/brand |
| BrandForm | 新增/编辑表单弹窗 | POST/PUT /product/brand，GET /product/brand/{id} |
| BrandStatusSwitch | 表格行内显示状态切换 | PUT /product/brand/{id}/show-status |
| LogoUpload | logo 上传（OSS 直传 MinIO） | mall-oss Presigned URL 接口 |
| CategoryCascaderMulti | 三级分类多选级联 | GET /product/category/tree（复用分类模块） |

### 关键交互

- **筛选查询**：name 模糊 + firstLetter 精确 + showStatus 精确，点查询触发 `GET /product/brand`；重置清空所有条件并重新查询
- **分页**：el-pagination，pageNum / pageSize 变化触发重新查询
- **新增 / 编辑**：表单校验通过后提交；编辑时携带 `version` 触发乐观锁；品牌名变更由后端同步刷新关联表冗余字段，前端无需额外处理
- **logo 上传**：前端先调 `mall-oss` 拿 Presigned URL → 直传 MinIO → 拿到对象访问 URL → 填入表单 `logo` 字段；品牌接口不接收文件流（详见 [对象存储服务设计](./object-storage-design.md)）
- **关联分类**：el-cascader 多选，仅叶子节点（三级）可选，提交时传 `categoryIds`；后端校验全部为三级分类，失败返回 `53004 BRAND_CATEGORY_INVALID`
- **显示状态切换**：el-switch 切换即调用 `PUT /product/brand/{id}/show-status`，失败回滚开关状态并提示
- **删除**：确认弹窗 → `DELETE /product/brand/{id}`；若后端返回 `53003 BRAND_HAS_PRODUCTS`，前端提示"品牌下存在关联商品，无法删除"
- **删除后列表刷新**：删除成功后停留在当前页，若当前页已无数据则回退到上一页

> 前台检索页（按分类筛品牌）消费 `GET /product/brand/by-category/{catelogId}`，属于前台展示模块，不在本管理后台页面范围内，待前台检索模块设计时统一规划。

---

## 十一、实现清单

| 序号 | 任务 | 文件 |
|------|------|------|
| 1 | 补充 ResultCode 品牌错误码 + 码段注释 | `mall-common/.../exception/ResultCode.java` |
| 2 | 创建 DTO/VO 类（5 个） | `mall-product/.../dto/brand/` |
| 3 | 创建分类关联实体 CategoryBrandRelation | `mall-product/.../entity/CategoryBrandRelation.java` |
| 4 | 创建 CategoryBrandRelationMapper | `mall-product/.../mapper/CategoryBrandRelationMapper.java` |
| 5 | 扩展 IBrandService 接口方法 | `mall-product/.../service/IBrandService.java` |
| 6 | 实现 BrandServiceImpl（含事务） | `mall-product/.../service/impl/BrandServiceImpl.java` |
| 7 | 创建 BrandController | `mall-product/.../controller/BrandController.java` |
| 8 | 创建 HTTP 调试文件 | `http/product-brand-demo.http` |
| 9 | 补充逻辑删除唯一索引 SQL（可选） | `init/mysql/mymall_pms.sql` |
