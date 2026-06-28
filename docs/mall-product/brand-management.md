# 品牌管理 - 需求与接口文档

> 模块：`mall-product`
> 表：`pms_brand`、`pms_category_brand_relation`
> 版本：v1.1
> 更新时间：2026-06-28

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
| F2 | 品牌详情 | 按 ID 查询品牌基础信息（不含关联分类，关联分类由 F8 独立管理） |
| F3 | 新增品牌 | 仅录入品牌基础信息（含 logo URL），不在此接口处理关联分类 |
| F4 | 修改品牌 | 修改品牌基础信息；品牌名变更时同步刷新关联表 `brand_name` 冗余字段 |
| F5 | 更新显示状态 | 单独切换显示/隐藏（`show_status`） |
| F6 | 删除品牌 | 逻辑删除，先校验 SPU 引用 |
| F7 | 查询分类下的品牌 | 前台检索用，按三级分类 ID 返回品牌列表 |
| F8 | 查询品牌关联分类列表 | 关联分类弹窗初始化用，返回品牌已关联的三级分类列表（含品牌名、分类名） |
| F9 | 新增品牌-分类关联 | 单条新增关联关系，冗余存储品牌名/分类名 |
| F10 | 移除品牌-分类关联 | 单条移除关联关系（逻辑删除） |
| F11 | 批量删除品牌 | 接收 id 列表，逐个做 SPU 引用检查后统一逻辑删除 |

### 2.2 业务规则

#### F1 分页查询品牌

- 仅返回 `is_deleted = 0` 的品牌（MyBatis-Plus `@TableLogic` 自动过滤）
- 按 `sort` 升序 → `id` 升序排列
- `name` 模糊匹配（`LIKE %name%`）
- `firstLetter` 精确匹配，`showStatus` 精确匹配
- `pageNum` 默认 1，`pageSize` 默认 10、最大 100

#### F2 品牌详情

- 仅返回品牌基础字段，**不返回** `categoryIds`（关联分类由 F8 独立接口提供，避免详情接口与关联分类管理职责耦合）
- 不存在抛 `BRAND_NOT_FOUND`

#### F3 新增品牌

- 品牌名全局唯一（`pms_brand.uk_name`）
- `logo` 为 OSS 直传后返回的访问 URL，前端必传
- `showStatus` 默认 1（显示），可选传
- `firstLetter` 可选；传则必须为单个小写/大写字母，后端统一转大写存储
- `sort` 默认 0
- **不处理关联分类**：关联分类由独立的 F9 接口维护，新增品牌后用户在「关联分类」弹窗中单独添加
- 审计字段（`create_by` / `update_by`）由 `MyMetaObjectHandler` 自动填充

#### F4 修改品牌

- `id` 必填，校验品牌存在
- 品牌名变更时校验全局唯一（排除自身）
- 品牌名变更时同步刷新 `pms_category_brand_relation.brand_name`
- **不处理关联分类**：关联分类增删由 F9/F10 独立维护
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

#### F8 查询品牌关联分类列表

- 路径参数为品牌 ID
- 查询 `pms_category_brand_relation`（`brand_id = ?` 且 `is_deleted = 0`）
- 返回关联记录列表，含 `brandId` / `brandName` / `catelogId` / `catelogName`（冗余字段直接取自关联表，避免联表）
- 按 `id` 升序排列

#### F9 新增品牌-分类关联

- 请求体含 `brandId` + `catelogId`
- 校验品牌存在（`is_deleted = 0`），不存在抛 `BRAND_NOT_FOUND`
- 校验分类存在且为三级分类（`cat_level = 3`），否则抛 `BRAND_CATEGORY_INVALID`
- 校验关联不重复（`uk_brand_catelog`），重复抛 `BRAND_RELATION_DUPLICATE`
- 写入 `pms_category_brand_relation`，冗余存储当前 `brand.name` 与 `category.name`（由 `MyMetaObjectHandler` 填充审计字段）

#### F10 移除品牌-分类关联

- 路径参数为 `brandId` + `catelogId`
- 校验关联记录存在（`is_deleted = 0`），不存在抛 `BRAND_RELATION_NOT_FOUND`
- 逻辑删除该条关联（`is_deleted = 1`）

#### F11 批量删除品牌

- 请求体为 `ids: List<Long>`，不允许为空，否则抛 `BRAND_BATCH_DELETE_EMPTY`
- 逐个执行 F6 的引用检查：若任一品牌存在 SPU 引用，整体事务回滚并返回该品牌名（错误码 `BRAND_HAS_PRODUCTS`）
- 全部通过后统一逻辑删除品牌 + 该品牌下的全部关联记录（同一事务）

---

## 三、数据模型

### 3.1 表结构 `pms_brand`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 主键（BaseEntity） |
| `name` | varchar(64) | 是 | 品牌名，全局唯一 |
| `logo` | varchar(1024) | 是 | 品牌 logo URL（OSS 直传后返回，前端必传） |
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
| 品牌详情 | GET | `/product/brand/{id}` | 仅返回品牌基础字段 |
| 新增品牌 | POST | `/product/brand` | 仅录入基础信息（含 logo URL），不含关联分类 |
| 修改品牌 | PUT | `/product/brand` | 修改基础信息；改名时同步刷新关联表 `brand_name` |
| 更新显示状态 | PUT | `/product/brand/{id}/show-status` | 切换显示/隐藏 |
| 删除品牌 | DELETE | `/product/brand/{id}` | 逻辑删除 + 引用检查 |
| 批量删除品牌 | DELETE | `/product/brand/batch` | 批量逻辑删除 + 逐个引用检查 |
| 分类下品牌 | GET | `/product/brand/by-category/{catelogId}` | 前台检索用 |
| 查询品牌关联分类 | GET | `/product/brand/{brandId}/category` | 关联分类弹窗初始化 |
| 新增品牌-分类关联 | POST | `/product/brand/category` | 单条新增关联，冗余存储品牌名/分类名 |
| 移除品牌-分类关联 | DELETE | `/product/brand/{brandId}/category/{catelogId}` | 单条逻辑删除关联 |

> 所有接口经过网关（`localhost:1000`），前端 `baseUrl` 为 `/api`，网关将 `/api/product/**` 路由到 `mall-product` 服务（`StripPrefix=1`）。
>
> **关联分类独立管理**：品牌新增/修改接口不处理关联分类，关联分类的增删查由独立接口承担（对应前端「关联分类」弹窗）。品牌改名、分类改名时分别通过关联关系 service 内部方法 `updateBrandName` / `updateCatelogName` 同步刷新冗余字段（见 4.10）。

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
        "sort": 0
    }
}
```

**实现要点**：
- 查品牌主记录，不存在抛 `BRAND_NOT_FOUND`
- 仅返回基础字段，关联分类由 4.9 独立接口提供

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
    "sort": 0
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
| `version` | Integer | 修改时必填 | 乐观锁版本号 |

> 新增/修改复用同一个 `BrandSaveDTO`，通过 `@Validated(Create.class)` / `@Validated(Update.class)` 区分（见 [Controller 规范 - 校验分组](../standards/controller-specification.md)）。
> **不含 `categoryIds`**：关联分类由独立接口（4.10/4.11）维护。

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// 品牌名重复
{ "code": 53002, "msg": "品牌名已存在", "data": null }
```

**业务逻辑**：

```
1. 校验品牌名唯一（排除逻辑删除），重复抛 BRAND_NAME_DUPLICATE
2. firstLetter 非空时校验为字母，统一转大写
3. showStatus 为空时默认 1
4. 插入 pms_brand（审计字段由 MyMetaObjectHandler 填充）
   （仅品牌主表写入，不涉及关联表）
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
4. 品牌名变更 → 调用关联关系 service 的 updateBrandName(brandId, newName)
   （UPDATE pms_category_brand_relation SET brand_name=? WHERE brand_id=? AND is_deleted=0）
5. 不处理 categoryIds（关联分类增删由 4.10/4.11 独立维护）
   （第 3、4 步在同一事务 @Transactional）
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

### 4.9 查询品牌关联分类列表

**GET** `/product/brand/{brandId}/category`

> 关联分类弹窗初始化用。返回当前品牌已关联的三级分类列表，直接读取关联表冗余字段，不联表 `pms_brand` / `pms_category`。

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": [
        {
            "id": 11,
            "brandId": 1,
            "brandName": "小米",
            "catelogId": 225,
            "catelogName": "手机"
        },
        {
            "id": 12,
            "brandId": 1,
            "brandName": "小米",
            "catelogId": 226,
            "catelogName": "平板电脑"
        }
    ]
}
```

**业务逻辑**：

```
1. 校验品牌存在（is_deleted = 0），不存在抛 BRAND_NOT_FOUND
2. 查询 pms_category_brand_relation WHERE brand_id=? AND is_deleted=0 ORDER BY id ASC
3. 直接映射为 BrandRelationVO 列表（brandName / catelogName 取冗余字段）
```

### 4.10 新增品牌-分类关联

**POST** `/product/brand/category`

**请求体**（`BrandCategoryRelationSaveDTO`）：

```json
{
    "brandId": 1,
    "catelogId": 225
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `brandId` | Long | 是 | `@NotNull`，品牌需存在 |
| `catelogId` | Long | 是 | `@NotNull`，需为三级分类 |

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// 品牌不存在
{ "code": 53001, "msg": "品牌不存在", "data": null }

// 分类不存在或非三级
{ "code": 53004, "msg": "关联分类 [999] 不存在或非三级分类", "data": null }

// 关联已存在
{ "code": 53006, "msg": "品牌与分类的关联已存在", "data": null }
```

**业务逻辑**：

```
1. 校验品牌存在（is_deleted = 0），不存在抛 BRAND_NOT_FOUND
2. 校验分类存在且 cat_level = 3，否则抛 BRAND_CATEGORY_INVALID
3. 校验关联不重复（is_deleted = 0 的记录中不存在同 brandId+catelogId），重复抛 BRAND_RELATION_DUPLICATE
4. 组装 pms_category_brand_relation 记录：
   - brand_name 取当前 pms_brand.name
   - catelog_name 取当前 pms_category.name
5. 插入关联表（审计字段由 MyMetaObjectHandler 填充）
```

> **冗余字段同步接口（供内部调用，非 HTTP 接口）**：在 `CategoryBrandRelationService` 中提供两个内部方法，供品牌/分类改名时调用：
> - `updateBrandName(Long brandId, String newName)`：刷新该品牌所有未删除关联的 `brand_name`
> - `updateCatelogName(Long catelogId, String newCatelogName)`：刷新该分类所有未删除关联的 `catelog_name`
>
> 调用方：
> - `BrandServiceImpl.update()` 改名时调用 `updateBrandName`
> - `CategoryServiceImpl.update()` 改名时通过 Feign/直接调用 `updateCatelogName`（分类管理模块实现时补充调用点）

### 4.11 移除品牌-分类关联

**DELETE** `/product/brand/{brandId}/category/{catelogId}`

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// 关联不存在
{ "code": 53007, "msg": "品牌-分类关联不存在", "data": null }
```

**业务逻辑**：

```
1. 查询 pms_category_brand_relation WHERE brand_id=? AND catelog_id=? AND is_deleted=0
2. 不存在抛 BRAND_RELATION_NOT_FOUND
3. 逻辑删除：UPDATE ... SET is_deleted=1 WHERE id=?
```

### 4.12 批量删除品牌

**DELETE** `/product/brand/batch`

**请求体**（`BrandBatchDeleteDTO`）：

```json
{
    "ids": [1, 2, 3]
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `ids` | `List<Long>` | 是 | `@NotEmpty`，每个元素 `@NotNull` |

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// id 列表为空
{ "code": 53008, "msg": "批量删除 id 列表不能为空", "data": null }

// 有 SPU 引用，整体回滚
{ "code": 53003, "msg": "品牌 [小米] 下存在关联商品，无法删除", "data": null }
```

**业务逻辑**：

```
1. ids 为空抛 BRAND_BATCH_DELETE_EMPTY
2. 逐个执行 F6 引用检查：
   SELECT count(*) FROM pms_spu_info WHERE brand_id=? AND is_deleted=0
   - 任一 > 0 → 抛 BRAND_HAS_PRODUCTS，携带该品牌名（整体事务回滚，不部分成功）
3. 全部通过后：
   a. 逻辑删除品牌：UPDATE pms_brand SET is_deleted=1 WHERE id IN (ids)
   b. 逻辑删除关联：UPDATE pms_category_brand_relation SET is_deleted=1 WHERE brand_id IN (ids)
   （整个流程在同一事务 @Transactional）
```

---

## 五、DTO / VO 定义

```
com.mymall.product.dto.brand/
├── BrandSaveDTO.java                    // 新增/修改品牌（校验分组 Create/Update）
├── BrandQueryDTO.java                   // 分页查询条件
├── BrandShowStatusDTO.java              // 更新显示状态
├── BrandBatchDeleteDTO.java             // 批量删除
├── BrandCategoryRelationSaveDTO.java    // 新增品牌-分类关联
├── BrandVO.java                         // 品牌详情/列表项
├── BrandSimpleVO.java                   // 分类下品牌精简返回（前台检索）
└── BrandRelationVO.java                 // 品牌关联分类列表项（关联分类弹窗）
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
}
```

> 关联分类列表由独立接口 4.9 返回 `BrandRelationVO`，不在 `BrandVO` 中耦合。

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

### 5.6 BrandCategoryRelationSaveDTO

```java
@Data
@Schema(description = "新增品牌-分类关联")
public class BrandCategoryRelationSaveDTO {

    @NotNull(message = "品牌ID不能为空")
    @Schema(description = "品牌ID")
    private Long brandId;

    @NotNull(message = "分类ID不能为空")
    @Schema(description = "三级分类ID")
    private Long catelogId;
}
```

### 5.7 BrandRelationVO

```java
@Data
@Schema(description = "品牌关联分类列表项")
public class BrandRelationVO {
    private Long id;
    private Long brandId;
    private String brandName;
    private Long catelogId;
    private String catelogName;
}
```

### 5.8 BrandBatchDeleteDTO

```java
@Data
@Schema(description = "批量删除品牌")
public class BrandBatchDeleteDTO {

    @NotEmpty(message = "id列表不能为空")
    @Schema(description = "品牌ID列表")
    private List<Long> ids;
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
| 53004 | `BRAND_CATEGORY_INVALID` | 关联分类不存在或非三级分类 | 新增关联时 `catelogId` 校验失败 |
| 53005 | `BRAND_SHOW_STATUS_INVALID` | 显示状态值非法 | `showStatus` 非 0/1 |
| 53006 | `BRAND_RELATION_DUPLICATE` | 品牌-分类关联已存在 | 新增关联违反 `uk_brand_catelog` |
| 53007 | `BRAND_RELATION_NOT_FOUND` | 品牌-分类关联不存在 | 移除关联时记录不存在 |
| 53008 | `BRAND_BATCH_DELETE_EMPTY` | 批量删除 id 列表不能为空 | `ids` 为空 |

对应枚举定义（追加到 `ResultCode`）：

```java
// ==================== 商品品牌 53001+ ====================
BRAND_NOT_FOUND(53001, "品牌不存在"),
BRAND_NAME_DUPLICATE(53002, "品牌名已存在"),
BRAND_HAS_PRODUCTS(53003, "品牌下存在关联商品，无法删除"),
BRAND_CATEGORY_INVALID(53004, "关联分类不存在或非三级分类"),
BRAND_SHOW_STATUS_INVALID(53005, "显示状态值非法"),
BRAND_RELATION_DUPLICATE(53006, "品牌-分类关联已存在"),
BRAND_RELATION_NOT_FOUND(53007, "品牌-分类关联不存在"),
BRAND_BATCH_DELETE_EMPTY(53008, "批量删除 id 列表不能为空"),
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

### 3. 新增品牌（仅基础信息，不含关联分类）
POST http://localhost:1000/api/product/brand
Content-Type: application/json

{
    "name": "小米",
    "logo": "https://oss.example.com/brand/mi.png",
    "descript": "为发烧而生",
    "showStatus": 1,
    "firstLetter": "X",
    "sort": 0
}

### 4. 修改品牌（不处理关联分类）
PUT http://localhost:1000/api/product/brand
Content-Type: application/json

{
    "id": 1,
    "name": "小米",
    "logo": "https://oss.example.com/brand/mi-new.png",
    "showStatus": 1,
    "firstLetter": "X",
    "sort": 0,
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

### 8. 批量删除品牌
DELETE http://localhost:1000/api/product/brand/batch
Content-Type: application/json

{
    "ids": [1, 2, 3]
}

### 9. 查询品牌关联分类列表（关联分类弹窗初始化）
GET http://localhost:1000/api/product/brand/1/category

### 10. 新增品牌-分类关联
POST http://localhost:1000/api/product/brand/category
Content-Type: application/json

{
    "brandId": 1,
    "catelogId": 225
}

### 11. 移除品牌-分类关联
DELETE http://localhost:1000/api/product/brand/1/category/225
```

---

## 九、非功能性要求

| 项目 | 要求 |
|------|------|
| 性能 | 分页查询 < 200ms（命中 `idx_catelog_id` / `uk_name`）；分类下品牌查询走关联表索引 |
| 并发 | 品牌为低频写操作，乐观锁（`@Version`）即可，无需分布式锁 |
| 缓存 | 品牌列表变更低频，可缓存分类下品牌到 Redis（TTL 30 分钟），写操作后主动失效 |
| 事务 | 新增/修改品牌仅写主表；删除/批量删除涉及 `pms_brand` + `pms_category_brand_relation` 双表，必须 `@Transactional` |
| 安全 | 管理接口需管理员权限（网关 JWT 鉴权，待实现）；`by-category` 接口可匿名访问 |
| 日志 | 写操作记录操作人 + 变更内容（审计需要），查询不记录 |
| 幂等 | 新增靠 `uk_name` 唯一约束兜底重复提交；其余为覆盖写，天然幂等 |
| 一致性 | 品牌改名 → 同步刷新 `pms_category_brand_relation.brand_name`；分类改名 → 同步刷新 `pms_category_brand_relation.catelog_name`（均通过关联关系 service 的 `updateBrandName` / `updateCatelogName` 内部方法） |

---

## 十、前端设计

### 页面布局

> Figma 设计稿：[品牌管理 Section](https://www.figma.com/design/UPX2R9RSakc3klzVIEB6Hq/My-mall?node-id=38-626)
> - [品牌列表页](https://www.figma.com/design/UPX2R9RSakc3klzVIEB6Hq/My-mall?node-id=38-627)
> - [新增/编辑品牌弹窗](https://www.figma.com/design/UPX2R9RSakc3klzVIEB6Hq/My-mall?node-id=40-116)
> - [关联分类弹窗](https://www.figma.com/design/UPX2R9RSakc3klzVIEB6Hq/My-mall?node-id=38-786)

**品牌列表页**（管理后台 `/product/brand`）

- 顶部筛选区（Toolbar）：品牌名输入框（200×32）+ 首字母下拉（120×32，A-Z）+ 显示状态下拉（100×32，全部/显示/隐藏）+ 查询按钮（主色，72×32）+ 竖线分隔符 + 新增品牌按钮（主色，96×32）+ 批量删除按钮（危险色，96×32）
- 表格区（列宽与筛选区严格对齐，从 x=20 开始）：
  - 复选框列（60px）：行内复选框
  - 品牌 Logo 列（120px）：logo 缩略图（60×40 圆角卡片 + "Logo" 占位文字）或"暂无"
  - 品牌名称列（180px）：品牌名（Medium 字体）
  - 品牌介绍列（380px）：单行截断展示（最多 28 字符 + "…"）
  - 首字母列（100px）：首字母，居中
  - 排序列（100px）：数字，居中
  - 显示状态列（120px）：el-switch（绿色开启 / 灰色关闭）
  - 操作列（292px）：✎ 编辑 / 🔗 关联分类 / 🗑 删除（三个动作均使用蓝色主色，删除用红色危险色）
- 底部分页区：共 4 条 + 10条/页下拉 + 页码按钮（‹ 1 ›）+ 前往页码输入框 + 页

**品牌表单弹窗**（新增 / 编辑复用，520×626，8px 圆角带浅灰描边）

- 弹窗头部（高 56px）：标题"新增品牌"（或"编辑品牌"）+ 右侧 ✕ 关闭按钮 + 底部 1px 浅灰分隔线
- 表单区（高 510px）：
  - 品牌名称（必填，y=24，h=40）：单行文本输入框，placeholder "请输入品牌名称"
  - 品牌 Logo（必填，y=80，120×80 虚线/浅灰上传区）：📷图标 + "点击上传" + "支持 JPG/PNG，≤ 10MB" 提示
  - 品牌介绍（y=176，h=60）：多行 textarea，placeholder "请输入品牌介绍（≤ 500字符）"
  - 首字母（y=252，h=40，120 宽）+ 排序（同行，h=40，100 宽，间距 20px）
  - 显示状态（y=322）：绿色 switch 默认开启 + 文字说明 "默认开启，开启后前台分类筛选可显示"
- 弹窗底部（高 60px）：取消按钮（白底边框）+ 确定按钮（主色填充），顶部 1px 浅灰分隔线

**关联分类弹窗**（520×480，8px 圆角带浅灰描边）

- 弹窗头部（高 56px）：标题"关联分类" + 右侧 ✕ 关闭按钮
- Toolbar（高 56px）：+ 新增关联 按钮（主色，96×32）+ 底部分隔线
- 表格区（4 列：#、品牌名、分类名、操作，居中对齐）：
  - 表格行展示当前品牌已关联的分类列表（如华为→手机、平板电脑、平板电视、智能穿戴、路由器）
  - 操作列提供"移除"动作（红色危险色文字）
- 弹窗底部（高 60px）：取消 + 确定按钮

### 组件拆分

| 组件 | 职责 | 消费接口 |
|------|------|---------|
| BrandList | 列表页主体：筛选 + 表格 + 分页 + 批量删除 | GET /product/brand，DELETE /product/brand/batch |
| BrandForm | 新增/编辑表单弹窗（仅基础信息，不含关联分类） | POST/PUT /product/brand，GET /product/brand/{id} |
| BrandRelationDialog | 关联分类管理弹窗（查询/新增/移除品牌-分类关联） | GET /product/brand/{brandId}/category，POST /product/brand/category，DELETE /product/brand/{brandId}/category/{catelogId} |
| BrandStatusSwitch | 表格行内显示状态切换 | PUT /product/brand/{id}/show-status |
| LogoUpload | logo 上传（OSS 直传 MinIO） | mall-oss Presigned URL 接口 |

### 关键交互

- **筛选查询**：name 模糊 + firstLetter 精确 + showStatus 精确，点查询触发 `GET /product/brand`；重置清空所有条件并重新查询
- **分页**：el-pagination，pageNum / pageSize 变化触发重新查询
- **新增 / 编辑**：表单校验通过后提交；编辑时携带 `version` 触发乐观锁；品牌名变更由后端同步刷新关联表冗余字段，前端无需额外处理
- **关联分类**：从列表页操作列的 🔗「关联分类」按钮触发 → 弹出关联分类弹窗，展示当前品牌已关联的分类列表（#、品牌名、分类名、操作），支持「新增关联」和「移除」操作。**品牌表单弹窗中不包含关联分类字段**，关联关系统一由独立弹窗管理，避免表单过于复杂。
- **logo 上传**：前端先调 `mall-oss` 拿 Presigned URL → 直传 MinIO → 拿到对象访问 URL → 填入表单 `logo` 字段；品牌接口不接收文件流（详见 [对象存储服务设计](./object-storage-design.md)）
- **显示状态切换**：el-switch 切换即调用 `PUT /product/brand/{id}/show-status`，失败回滚开关状态并提示
- **删除**：确认弹窗 → `DELETE /product/brand/{id}`；若后端返回 `53003 BRAND_HAS_PRODUCTS`，前端提示"品牌下存在关联商品，无法删除"
- **删除后列表刷新**：删除成功后停留在当前页，若当前页已无数据则回退到上一页

> 前台检索页（按分类筛品牌）消费 `GET /product/brand/by-category/{catelogId}`，属于前台展示模块，不在本管理后台页面范围内，待前台检索模块设计时统一规划。

---

## 十一、实现清单

| 序号 | 任务 | 文件 |
|------|------|------|
| 1 | 补充 ResultCode 品牌错误码（53001~53008）+ 码段注释 | `mall-common/.../exception/ResultCode.java` |
| 2 | 创建 DTO/VO 类（8 个） | `mall-product/.../dto/brand/` |
| 3 | 创建分类关联实体 CategoryBrandRelation | `mall-product/.../entity/CategoryBrandRelation.java` |
| 4 | 创建 CategoryBrandRelationMapper | `mall-product/.../mapper/CategoryBrandRelationMapper.java` |
| 5 | 扩展 IBrandService 接口方法（含批量删除） | `mall-product/.../service/IBrandService.java` |
| 6 | 实现 BrandServiceImpl（含事务，改名时调用关联 service 刷新冗余） | `mall-product/.../service/impl/BrandServiceImpl.java` |
| 7 | 创建 ICategoryBrandRelationService 接口（含 updateBrandName / updateCatelogName 内部方法） | `mall-product/.../service/ICategoryBrandRelationService.java` |
| 8 | 实现 CategoryBrandRelationServiceImpl | `mall-product/.../service/impl/CategoryBrandRelationServiceImpl.java` |
| 9 | 创建 BrandController（含品牌 CRUD + 批量删除 + 关联分类三个接口） | `mall-product/.../controller/BrandController.java` |
| 10 | 创建 HTTP 调试文件 | `http/product-brand-demo.http` |
| 11 | 补充逻辑删除唯一索引 SQL（可选） | `init/mysql/mymall_pms.sql` |
| 12 | 分类管理模块实现 update 分类时，调用关联 service 的 `updateCatelogName` 同步冗余字段 | `mall-product/.../service/impl/CategoryServiceImpl.java`（分类管理实现时补充） |
