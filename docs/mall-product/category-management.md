# 商品分类管理 - 需求与接口文档

> 模块：`mall-product`  
> 表：`pms_category`  
> 版本：v1.0  
> 更新时间：2026-06-22

---

## 一、业务背景

商品分类是电商系统的核心基础数据，采用**三级分类**结构（一级 → 二级 → 三级）。分类数据被以下模块引用：

| 引用模块 | 表 | 字段 |
|---------|---|------|
| 商品 | `pms_spu_info` | `category_id` |
| 品牌 | `pms_category_brand_relation` | `catelog_id` |
| 优惠券 | `sms_coupon_spu_category_relation` | `category_id` |

分类的变更（删除、层级调整）必须评估对以上模块的影响。

---

## 二、功能需求

### 2.1 功能清单

| 编号 | 功能 | 说明 |
|------|------|------|
| F1 | 分类树查询 | 查出所有分类及子分类，以树形结构返回，按 sort 字段排序 |
| F2 | 批量删除分类 | 接受 ID 列表，先检查引用再逻辑删除（show_status 置 0） |
| F3 | 新增分类 | 支持填写名称、父级、排序、图标、计量单位等 |
| F4 | 修改分类 | 支持单个字段更新（如名称、图标）和拖拽操作（切换父节点、调整层级/排序） |

### 2.2 业务规则

#### F1 分类树查询

- 仅返回 `show_status = 1` 的分类
- 按 `sort` 升序排列（sort 相同时按 `cat_id` 升序）
- 前端树形控件需要 `children` 字段嵌套子分类
- 三级分类最大深度为 3，超过不允许

#### F2 批量删除分类

- **引用检查**：被删除的分类（含子分类）下不能有关联数据
  - 不能有关联商品（`pms_spu_info.category_id`）
  - 不能有关联品牌（`pms_category_brand_relation.catelog_id`）
  - 不能有关联优惠券分类（`sms_coupon_spu_category_relation.category_id`）
- **级联子分类**：删除父分类时，其所有子孙分类必须一并检查引用
- **逻辑删除**：不物理删除记录，将 `show_status` 置为 `0`（隐藏）
- **根分类保护**：`parent_cid = 0` 的一级分类不允许删除（防止误操作清空整个分类树）

#### F3 新增分类

- 分类名称在同级下唯一（同一 `parent_cid` 下 `name` 不能重复）
- `cat_level` 由父分类 `catLevel + 1` 自动计算，前端不传
- 层级限制：`cat_level` 最大为 3（不允许创建四级分类）
- `sort` 默认值 0，前端可指定

#### F4 修改分类

- **基础信息修改**：名称、图标、排序、计量单位等
- **拖拽排序**：前端拖拽节点到新位置，传入新的 `parent_cid`、`cat_level`、`sort`
- **批量拖拽**：多个节点同时拖拽，传入排序列表
- 拖拽后需重新计算受影响分类的 `cat_level`（子分类的层级跟随父分类变化）
- 拖拽限制：不能将自己拖拽为自己的子节点（循环引用）

---

## 三、数据模型

### 3.1 表结构 `pms_category`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `cat_id` | bigint AUTO_INCREMENT | PK | 分类 ID |
| `name` | char(50) | 是 | 分类名称 |
| `parent_cid` | bigint | 是 | 父分类 ID，一级分类为 0 |
| `cat_level` | int | 是 | 层级（1/2/3） |
| `show_status` | tinyint | 是 | 显示状态：0-隐藏，1-显示 |
| `sort` | int | 是 | 排序值，越小越靠前 |
| `icon` | char(255) | 否 | 图标地址 |
| `product_unit` | char(50) | 否 | 计量单位 |
| `product_count` | int | 否 | 商品数量（冗余，定时刷新） |

### 3.2 索引

| 索引 | 字段 | 类型 | 说明 |
|------|------|------|------|
| PK | `cat_id` | PRIMARY | 主键 |
| IDX_PARENT | `parent_cid, show_status, sort` | NORMAL | 子分类查询（覆盖排序） |
| UK_PARENT_NAME | `parent_cid, name` | UNIQUE | 同级名称唯一 |

> 生产环境需执行索引创建 SQL：
> ```sql
> ALTER TABLE pms_category ADD INDEX idx_parent (parent_cid, show_status, sort);
> ALTER TABLE pms_category ADD UNIQUE INDEX uk_parent_name (parent_cid, name);
> ```

---

## 四、接口设计

### 4.1 接口总览

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 分类树 | GET | `/product/category/tree` | 返回完整三级树 |
| 批量删除 | POST | `/product/category/batch-delete` | 逻辑删除（含引用检查） |
| 新增分类 | POST | `/product/category` | 创建分类节点 |
| 修改分类 | PUT | `/product/category` | 修改基础信息 |
| 拖拽排序 | PUT | `/product/category/sort` | 拖拽调整层级/排序 |

> 所有接口经过网关（`localhost:1000`），前端 `baseUrl` 为 `/api`，网关将 `/api/product/**` 路由到 `mall-product` 服务（StripPrefix=1 去掉 `/api` 前缀）。

### 4.2 分类树

**GET** `/product/category/tree`

**请求参数**：无

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": [
        {
            "catId": 1,
            "name": "图书、音像、电子书刊",
            "parentCid": 0,
            "catLevel": 1,
            "showStatus": 1,
            "sort": 0,
            "icon": "https://xxx.com/icon1.png",
            "productUnit": "件",
            "productCount": 128,
            "children": [
                {
                    "catId": 2,
                    "name": "电子书刊",
                    "parentCid": 1,
                    "catLevel": 2,
                    "showStatus": 1,
                    "sort": 0,
                    "icon": null,
                    "productUnit": null,
                    "productCount": 36,
                    "children": [
                        {
                            "catId": 3,
                            "name": "电子书",
                            "parentCid": 2,
                            "catLevel": 3,
                            "showStatus": 1,
                            "sort": 0,
                            "icon": null,
                            "productUnit": null,
                            "productCount": 12,
                            "children": []
                        }
                    ]
                }
            ]
        }
    ]
}
```

**实现要点**：
- 一次查询所有 `show_status = 1` 的分类（数据量小，不超过 1000 条，不需要分页）
- Java 内存中组装树形结构（Stream API）
- 按 `sort` 升序 → `catId` 升序排列

### 4.3 批量删除

**POST** `/product/category/batch-delete`

**请求体**：

```json
{
    "ids": [1, 2, 3]
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `ids` | `List<Long>` | 是 | `@NotEmpty`，最多 100 个 |

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// 有引用数据，拒绝删除
{ "code": 50001, "msg": "分类 [电子书刊] 下存在关联商品，无法删除", "data": null }
```

**业务逻辑**：

```
1. 查询 ids 对应的所有分类
2. 检查是否为一级分类（parent_cid = 0），是则拒绝
3. 递归查询每个分类的所有子孙分类 ID
4. 合并所有待删除分类 ID（含子孙）
5. 检查引用：
   a. pms_spu_info 中是否有 category_id 在待删除列表中
   b. pms_category_brand_relation 中是否有 catelog_id 在待删除列表中
   c. sms_coupon_spu_category_relation 中是否有 category_id 在待删除列表中
6. 任一引用存在 → 抛 BizException，返回具体被引用的分类名
7. 无引用 → UPDATE pms_category SET show_status = 0 WHERE cat_id IN (...)
```

### 4.4 新增分类

**POST** `/product/category`

**请求体**（`CategorySaveDTO`）：

```json
{
    "name": "手机通讯",
    "parentCid": 1,
    "sort": 5,
    "icon": "https://xxx.com/icon.png",
    "productUnit": "台"
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `name` | String | 是 | `@NotBlank`，最大 50 字符 |
| `parentCid` | Long | 是 | `@NotNull`，一级分类传 0 |
| `sort` | Integer | 否 | 默认 0，`@Min(0)` |
| `icon` | String | 否 | URL 格式，最大 255 字符 |
| `productUnit` | String | 否 | 最大 50 字符 |

> `catLevel` 由后端根据 `parentCid` 自动计算（父分类 `catLevel + 1`），前端不传。

**响应**：

```json
{ "code": 200, "msg": "success", "data": {} }
```

**业务逻辑**：

```
1. 如果 parentCid != 0：
   a. 查询父分类，不存在则抛异常
   b. 计算 catLevel = 父分类.catLevel + 1
   c. 如果 catLevel > 3，抛异常"分类最多支持三级"
2. 如果 parentCid == 0，catLevel = 1
3. 检查同级名称唯一（同 parentCid 下 name 不重复）
4. 设置 showStatus = 1，productCount = 0
5. 插入记录
```

### 4.5 修改分类

**PUT** `/product/category`

**请求体**（`CategoryUpdateDTO`）：

```json
{
    "catId": 5,
    "name": "智能手机",
    "sort": 3,
    "icon": "https://xxx.com/icon2.png",
    "productUnit": "台"
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `catId` | Long | 是 | `@NotNull` |
| `name` | String | 否 | 最大 50 字符，传则校验非空 |
| `sort` | Integer | 否 | `@Min(0)` |
| `icon` | String | 否 | 最大 255 字符 |
| `productUnit` | String | 否 | 最大 50 字符 |

**响应**：

```json
{ "code": 200, "msg": "success", "data": {} }
```

**业务逻辑**：

```
1. 查询分类，不存在则抛异常
2. 如果 name 有变化，检查同级名称唯一性
3. 更新非 null 字段
```

### 4.6 拖拽排序

**PUT** `/product/category/sort`

**请求体**（`CategorySortDTO`）：

```json
{
    "categories": [
        {
            "catId": 5,
            "parentCid": 2,
            "catLevel": 2,
            "sort": 1
        },
        {
            "catId": 6,
            "parentCid": 2,
            "catLevel": 2,
            "sort": 2
        },
        {
            "catId": 7,
            "parentCid": 1,
            "catLevel": 2,
            "sort": 1
        }
    ]
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `categories` | `List<SortItem>` | 是 | `@NotEmpty`，最多 100 |
| `categories[].catId` | Long | 是 | `@NotNull` |
| `categories[].parentCid` | Long | 是 | `@NotNull` |
| `categories[].catLevel` | Integer | 是 | `@NotNull`，范围 1~3 |
| `categories[].sort` | Integer | 是 | `@NotNull`，`@Min(0)` |

**响应**：

```json
{ "code": 200, "msg": "success", "data": {} }
```

**业务逻辑**：

```
1. 遍历 categories 列表：
   a. 检查 catId 对应的分类是否存在
   b. 如果 parentCid 有变化，检查不能形成循环引用
      （自己不能成为自己的祖先）
   c. 如果 catLevel 有变化，递归更新所有子孙分类的 catLevel
2. 批量更新 parent_cid、cat_level、sort 字段
```

---

## 五、DTO 定义

```
com.mymall.product.dto.category/
├── CategorySaveDTO.java        // 新增
├── CategoryUpdateDTO.java      // 修改
├── CategorySortDTO.java        // 拖拽排序
├── CategoryBatchDeleteDTO.java // 批量删除
└── CategoryVO.java             // 树形查询返回（含 children）
```

### 5.1 CategoryVO

```java
@Data
@Schema(description = "分类树节点")
public class CategoryVO {
    private Long catId;
    private String name;
    private Long parentCid;
    private Integer catLevel;
    private Integer showStatus;
    private Integer sort;
    private String icon;
    private String productUnit;
    private Integer productCount;

    @Schema(description = "子分类列表")
    private List<CategoryVO> children;
}
```

### 5.2 CategorySaveDTO

```java
@Data
@Schema(description = "新增分类")
public class CategorySaveDTO {
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最长 50 字符")
    @Schema(description = "分类名称", example = "手机通讯")
    private String name;

    @NotNull(message = "父分类ID不能为空")
    @Schema(description = "父分类ID，一级分类传 0", example = "1")
    private Long parentCid;

    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "排序值", example = "5")
    private Integer sort;

    @Size(max = 255, message = "图标地址最长 255 字符")
    @Schema(description = "图标地址")
    private String icon;

    @Size(max = 50, message = "计量单位最长 50 字符")
    @Schema(description = "计量单位", example = "台")
    private String productUnit;
}
```

### 5.3 CategoryUpdateDTO

```java
@Data
@Schema(description = "修改分类")
public class CategoryUpdateDTO {
    @NotNull(message = "分类ID不能为空")
    @Schema(description = "分类ID", example = "5")
    private Long catId;

    @Size(max = 50, message = "分类名称最长 50 字符")
    @Schema(description = "分类名称")
    private String name;

    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "排序值")
    private Integer sort;

    @Size(max = 255, message = "图标地址最长 255 字符")
    @Schema(description = "图标地址")
    private String icon;

    @Size(max = 50, message = "计量单位最长 50 字符")
    @Schema(description = "计量单位")
    private String productUnit;
}
```

### 5.4 CategorySortDTO

```java
@Data
@Schema(description = "拖拽排序")
public class CategorySortDTO {
    @NotEmpty(message = "排序列表不能为空")
    @Size(max = 100, message = "单次最多排序 100 个分类")
    @Valid
    private List<SortItem> categories;

    @Data
    public static class SortItem {
        @NotNull
        private Long catId;

        @NotNull
        private Long parentCid;

        @NotNull @Min(1) @Max(3)
        private Integer catLevel;

        @NotNull @Min(0)
        private Integer sort;
    }
}
```

### 5.5 CategoryBatchDeleteDTO

```java
@Data
@Schema(description = "批量删除分类")
public class CategoryBatchDeleteDTO {
    @NotEmpty(message = "ID列表不能为空")
    @Size(max = 100, message = "单次最多删除 100 个分类")
    @Schema(description = "分类ID列表", example = "[1, 2, 3]")
    private List<Long> ids;
}
```

---

## 六、错误码

在 `ResultCode` 枚举中补充商品分类相关错误码（50001+ 码段）：

| 错误码 | 枚举值 | 消息 | 触发场景 |
|--------|--------|------|---------|
| 50001 | `CATEGORY_NOT_FOUND` | 分类不存在 | ID 查询不到 |
| 50002 | `CATEGORY_HAS_CHILDREN` | 分类下存在子分类，无法删除 | 删除时有子分类被引用 |
| 50003 | `CATEGORY_HAS_PRODUCTS` | 分类下存在关联商品，无法删除 | 删除时 `pms_spu_info` 有引用 |
| 50004 | `CATEGORY_HAS_BRANDS` | 分类下存在关联品牌，无法删除 | 删除时品牌关联表有引用 |
| 50005 | `CATEGORY_LEVEL_EXCEEDED` | 分类最多支持三级 | 新增/拖拽时 catLevel > 3 |
| 50006 | `CATEGORY_NAME_DUPLICATE` | 同级分类名称已存在 | 新增/修改时名称重复 |
| 50007 | `CATEGORY_CIRCULAR_REF` | 不能将分类移动到自身的子节点下 | 拖拽循环引用 |
| 50008 | `CATEGORY_ROOT_DELETE` | 一级分类不允许删除 | 批量删除一级分类 |

> **注意**：以上错误码需与 `ResultCode` 已有的 50001/50002 协调（当前 `PRODUCT_NOT_FOUND=50001`、`PRODUCT_OFF_SHELF=50002`）。建议将分类错误码调整到 **51001+** 码段，避免冲突：

| 错误码 | 枚举值 | 消息 |
|--------|--------|------|
| 51001 | `CATEGORY_NOT_FOUND` | 分类不存在 |
| 51002 | `CATEGORY_HAS_PRODUCTS` | 分类下存在关联商品，无法删除 |
| 51003 | `CATEGORY_HAS_BRANDS` | 分类下存在关联品牌，无法删除 |
| 51004 | `CATEGORY_LEVEL_EXCEEDED` | 分类最多支持三级 |
| 51005 | `CATEGORY_NAME_DUPLICATE` | 同级分类名称已存在 |
| 51006 | `CATEGORY_CIRCULAR_REF` | 不能将分类移动到自身的子节点下 |
| 51007 | `CATEGORY_ROOT_DELETE` | 一级分类不允许删除 |

---

## 七、网关路由

前端管理后台通过网关访问分类接口：

```
浏览器 → http://localhost:1000/api/product/category/tree
                  │
                  ▼
         网关（mall-gateway:1000）
         路由：product-route
         Predicate: Path=/api/product/**
         Filter: StripPrefix=1
                  │
                  ▼
         lb://mall-product
         实际请求：/product/category/tree
```

> 网关已配置 `StripPrefix=1`，前端 `baseUrl=/api` 前缀在网关层被去掉。

---

## 八、HTTP 调试文件

`http/product-category-demo.http`

```http
### 1. 分类树查询
GET http://localhost:1000/api/product/category/tree

### 2. 新增一级分类
POST http://localhost:1000/api/product/category
Content-Type: application/json

{
    "name": "测试一级分类",
    "parentCid": 0,
    "sort": 100,
    "icon": "https://example.com/icon.png",
    "productUnit": "件"
}

### 3. 新增二级分类
POST http://localhost:1000/api/product/category
Content-Type: application/json

{
    "name": "测试二级分类",
    "parentCid": {{parentId}},
    "sort": 1,
    "productUnit": "个"
}

### 4. 修改分类名称
PUT http://localhost:1000/api/product/category
Content-Type: application/json

{
    "catId": {{catId}},
    "name": "修改后的名称",
    "sort": 2
}

### 5. 拖拽排序（将 catId=5 从一级移动到二级）
PUT http://localhost:1000/api/product/category/sort
Content-Type: application/json

{
    "categories": [
        { "catId": 5, "parentCid": 2, "catLevel": 2, "sort": 1 },
        { "catId": 6, "parentCid": 2, "catLevel": 2, "sort": 2 }
    ]
}

### 6. 批量删除
POST http://localhost:1000/api/product/category/batch-delete
Content-Type: application/json

{
    "ids": [10, 11]
}
```

---

## 九、非功能性要求

| 项目 | 要求 |
|------|------|
| 性能 | 分类树查询 < 100ms（数据量 < 1000，内存组装） |
| 并发 | 分类为低频写操作，不需要分布式锁 |
| 缓存 | 分类树可缓存到 Redis（TTL 10 分钟），写操作后主动失效缓存 |
| 安全 | 分类管理接口需要管理员权限（网关 JWT 鉴权，待实现） |
| 日志 | 写操作记录操作人 + 变更内容（审计需要） |
| 幂等 | 拖拽排序天然幂等（覆盖写），删除用逻辑删除不冲突 |

---

## 十、实现清单

| 序号 | 任务 | 文件 |
|------|------|------|
| 1 | 补充 ResultCode 分类错误码 | `mall-common/.../exception/ResultCode.java` |
| 2 | 创建 DTO 类（5 个） | `mall-product/.../dto/category/` |
| 3 | 创建 CategoryVO | `mall-product/.../dto/category/CategoryVO.java` |
| 4 | 实现 ICategoryService 方法 | `mall-product/.../service/ICategoryService.java` |
| 5 | 实现 CategoryServiceImpl | `mall-product/.../service/impl/CategoryServiceImpl.java` |
| 6 | 创建 CategoryController | `mall-product/.../controller/CategoryController.java` |
| 7 | 创建 HTTP 调试文件 | `http/product-category-demo.http` |
| 8 | 创建生产索引 SQL | `init/mysql/mymall_pms.sql` 补充索引 |
