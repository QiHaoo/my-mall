# SPU 管理 - 需求与接口文档

> 模块：`mall-product`
> 表：`pms_spu_info`、`pms_spu_info_desc`、`pms_spu_images`、`pms_product_attr_value`
> 版本：v1.0
> 更新时间：2026-06-27

---

## 一、业务背景

SPU（Standard Product Unit）是商品信息聚合的最小单位，描述"这一类商品"的共同特征。SPU 不可直接售卖，没有价格和库存，只是"商品档案"。

SPU 依赖分类、品牌、属性三类基础数据：
- 归属一个**三级分类**（`category_id`），决定可用属性范围
- 归属一个**品牌**（`brand_id`），品牌必须属于该分类
- 挂载多个**规格参数值**（基本属性取值），属性必须属于该分类

> **概念详解**：SPU/SKU/属性的概念与实体关系见 [商品中心概述](./overview.md)。

SPU 数据被以下模块引用：

| 引用模块 | 表 | 字段 | 说明 |
|---------|---|------|------|
| SKU | `pms_sku_info` | `spu_id` | SPU 派生多个 SKU |
| 检索 | OpenSearch | `spuId` | 上架 SPU 同步到搜索引擎 |
| 营销 | `sms_coupon_spu_relation` | `spu_id` | 优惠券关联 SPU |

SPU 的变更（删除、上下架）必须评估对以上模块的影响。

---

## 二、功能需求

### 2.1 功能清单

| 编号 | 功能 | 说明 |
|------|------|------|
| C1 | SPU 分页查询 | 按分类、品牌、上架状态、关键字筛选 |
| C2 | SPU 详情 | 含介绍、图片集、规格参数值 |
| C3 | 新增 SPU | 主表 + 介绍 + 图片集 + 规格参数值，事务写入 |
| C4 | 修改 SPU | 全量覆盖图片集与规格参数值 |
| C5 | SPU 上架/下架 | 切换 `publish_status` |
| C6 | 删除 SPU | 校验是否存在 SKU，逻辑删除级联从属表 |

### 2.2 业务规则

#### C1 SPU 分页查询

- 仅返回 `is_deleted = 0` 的 SPU
- 支持按 `category_id`、`brand_id`、`publish_status`、`spu_name`（模糊）筛选
- 按 `id` 降序排列（最新商品在前）
- `pageNum` 默认 1，`pageSize` 默认 10、最大 100

#### C2 SPU 详情

- 返回主表字段 + 介绍（`pms_spu_info_desc`）+ 图片集（`pms_spu_images`，按 `img_sort` 升序）+ 规格参数值（`pms_product_attr_value`）
- 规格参数值按属性分组组织返回（联查 `pms_attr_attrgroup_relation`），便于前端分区展示

#### C3 新增 SPU

- `category_id` 必须为三级分类，`brand_id` 必须存在且属于该分类（查 `pms_category_brand_relation`）
- 规格参数值：仅 `attr_type ∈ {1,2}` 的属性可填；`attr_id` 必须属于 SPU 的分类
- SPU 默认 `publish_status=0`（下架），需手动上架后才可被检索/购买
- 主表 + 介绍（`pms_spu_info_desc`）+ 图片集 + 规格参数值，**同一事务**写入
- 审计字段（`create_by` / `update_by`）由 `MyMetaObjectHandler` 自动填充

#### C4 修改 SPU

- `id` 必填，校验 SPU 存在
- 图片集与规格参数值采用**全量覆盖**（逻辑删旧 + 插新）
- 主表字段 `updateById`（携带 `version` 乐观锁）
- 品牌变更时校验新品牌属于该分类

#### C5 SPU 上架/下架

- `publish_status` 仅允许 0 或 1
- 上架后 SPU 及其 SKU 同步到 OpenSearch（Canal binlog → MQ）
- 下架后从 OpenSearch 移除，前台不可检索

#### C6 删除 SPU

- **引用检查**：`pms_sku_info` 存在 SKU 则拒绝（含逻辑删除的 SKU 不计入）
- **级联逻辑删除**：主表 + 介绍 + 图片集 + 规格参数值（同事务）
- 删除后从 OpenSearch 移除

---

## 三、数据模型

### 3.1 SPU 四表

| 表 | 关键字段 | 设计要点 |
|----|---------|---------|
| `pms_spu_info` | `category_id`、`brand_id`、`publish_status`、`weight` | 主表，索引 `idx_category_id` / `idx_brand_id` |
| `pms_spu_info_desc` | `spu_id`（UK）、`decript` | 1:1 扩展表，富文本拆表 |
| `pms_spu_images` | `spu_id`、`img_url`、`img_sort`、`default_img` | 图片集，按 `spu_id` 索引 |
| `pms_product_attr_value` | `spu_id`、`attr_id`、`attr_name`、`attr_value`、`quick_show` | 规格参数值，冗余 `attr_name` |

> **为什么介绍拆 1:1 扩展表？** `pms_spu_info` 主表会被列表/检索高频扫描，而 `decript` 是大段富文本（text）。拆到 `pms_spu_info_desc` 后，列表查询只读主表轻量字段，详情页才 join 扩展表取介绍。这是"垂直拆分"的典型实践。

### 3.2 索引补充建议

- 逻辑删除与唯一约束冲突的处理思路同 [品牌管理](./brand-management.md)

---

## 四、接口设计

### 4.1 接口总览

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| SPU 分页查询 | GET | `/product/spu/list` | 按分类/品牌/状态/关键字 |
| SPU 详情 | GET | `/product/spu/{id}` | 含介绍/图片/规格参数值 |
| 新增 SPU | POST | `/product/spu` | 事务写多表 |
| 修改 SPU | PUT | `/product/spu` | 全量覆盖从属 |
| SPU 上架/下架 | PUT | `/product/spu/{id}/publish` | 切换状态 |
| 删除 SPU | DELETE | `/product/spu/{id}` | 校验 SKU |

> 所有接口经网关，前端 `baseUrl=/api`，网关将 `/api/product/**` 路由到 `mall-product`（`StripPrefix=1`），与品牌/分类接口共用 `product-route`。

### 4.2 新增 SPU（最复杂接口，示例）

**POST** `/product/spu`

**请求体**（`SpuSaveDTO`）：

```json
{
    "spuName": "iPhone 15",
    "spuDescription": "Apple iPhone 15",
    "categoryId": 225,
    "brandId": 1,
    "weight": 171.0,
    "publishStatus": 0,
    "decript": "<富文本介绍>",
    "images": [
        { "imgUrl": "https://oss.example.com/spu/1.jpg", "imgSort": 0, "defaultImg": 1 },
        { "imgUrl": "https://oss.example.com/spu/2.jpg", "imgSort": 1, "defaultImg": 0 }
    ],
    "baseAttrs": [
        { "attrId": 1001, "attrName": "屏幕尺寸", "attrValue": "6.1英寸", "quickShow": 1 },
        { "attrId": 1002, "attrName": "CPU", "attrValue": "A16", "quickShow": 0 }
    ]
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `spuName` | String | 是 | `@NotBlank`，最大 200 |
| `categoryId` | Long | 是 | 存在的三级分类 |
| `brandId` | Long | 是 | 存在且属于该分类 |
| `weight` | BigDecimal | 否 | `@DecimalMin("0")` |
| `publishStatus` | Integer | 否 | 0/1，默认 0 |
| `decript` | String | 否 | 富文本介绍 |
| `images` | `List<SpuImageDTO>` | 否 | SPU 图片集 |
| `baseAttrs` | `List<SpuAttrValueDTO>` | 否 | 规格参数值，`attrId` 须为分类下基本属性 |

**业务逻辑**：

```
1. 校验 categoryId 为三级分类
2. 校验 brandId 属于该分类（pms_category_brand_relation）
3. 校验 baseAttrs 中每个 attrId 属于该分类且 attr_type ∈ {1,2}
4. 插入 pms_spu_info（审计字段自动填充）
5. 插入 pms_spu_info_desc（spu_id 关联）
6. 批量插入 pms_spu_images
7. 批量插入 pms_product_attr_value（冗余 attr_name）
   （4~7 同一事务 @Transactional）
```

**响应**：

```json
{ "code": 200, "msg": "success", "data": { "id": 1001 } }

// 品牌与分类不匹配
{ "code": 54022, "msg": "品牌 [1] 不属于分类 [225]", "data": null }
```

> 其余接口（SPU 修改/上下架/删除）体例与品牌接口一致，实现时按 [品牌管理](./brand-management.md) 的模板补全。

---

## 五、DTO / VO 定义

```
com.mymall.product.dto.spu/
├── SpuSaveDTO.java              // SPU 新增/修改
├── SpuQueryDTO.java             // SPU 分页查询
├── SpuImageDTO.java             // SPU 图片项
├── SpuAttrValueDTO.java         // 规格参数值项
└── SpuPublishDTO.java           // 上架/下架

com.mymall.product.vo/
└── SpuVO.java                   // SPU 详情（含介绍/图片/规格参数）
```

### 5.1 SpuSaveDTO（示意）

```java
@Data
@Schema(description = "新增/修改 SPU")
public class SpuSaveDTO {

    @NotNull(groups = Update.class, message = "SPU ID 不能为空")
    private Long id;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200)
    private String spuName;

    @Size(max = 1024)
    private String spuDescription;

    @NotNull(message = "所属分类不能为空")
    private Long categoryId;

    @NotNull(message = "品牌不能为空")
    private Long brandId;

    @DecimalMin(value = "0", message = "重量不能为负")
    private BigDecimal weight;

    @Schema(description = "上架状态：0-下架 1-上架")
    private Integer publishStatus;

    @Schema(description = "富文本介绍")
    private String decript;

    @Valid
    private List<SpuImageDTO> images;

    @Valid
    private List<SpuAttrValueDTO> baseAttrs;

    @NotNull(groups = Update.class, message = "版本号不能为空")
    private Integer version;
}
```

---

## 六、错误码

在 `ResultCode` 中补充 SPU 相关错误码（**54020~54024 码段**）：

| 错误码 | 枚举值 | 消息 | 触发场景 |
|--------|--------|------|---------|
| 54020 | `SPU_NOT_FOUND` | 商品不存在 | ID 查询不到 |
| 54021 | `SPU_CATEGORY_INVALID` | 分类不存在或非三级分类 | `categoryId` 校验失败 |
| 54022 | `SPU_BRAND_INVALID` | 品牌不属于该分类 | `brandId` 与分类不匹配 |
| 54023 | `SPU_HAS_SKUS` | 商品下存在 SKU，无法删除 | 删除 SPU 时有 SKU |
| 54024 | `SPU_ATTR_INVALID` | 规格参数不属于该分类或类型不匹配 | `baseAttrs` 校验失败 |

对应枚举（追加到 `ResultCode`）：

```java
// ==================== 商品 SPU 54020+ ====================
SPU_NOT_FOUND(54020, "商品不存在"),
SPU_CATEGORY_INVALID(54021, "分类不存在或非三级分类"),
SPU_BRAND_INVALID(54022, "品牌不属于该分类"),
SPU_HAS_SKUS(54023, "商品下存在 SKU，无法删除"),
SPU_ATTR_INVALID(54024, "规格参数不属于该分类或类型不匹配"),
```

> 同时在 `ResultCode` 头部码段规划注释中补：`54020~54024 - 商品 SPU`。

---

## 七、网关路由

与分类/品牌/属性/SKU 接口共用 `product-route` 路由，详见 [品牌管理](./brand-management.md)。

---

## 八、HTTP 调试文件

```
http/product-spu-demo.http
```

---

## 九、非功能性要求

> 模块级非功能性要求（性能、缓存、事务、检索同步等）见 [商品中心概述](./overview.md)。

---

## 十、实现清单

| 序号 | 任务 | 文件 |
|------|------|------|
| 1 | 补充 ResultCode SPU 错误码 + 码段注释 | `mall-common/.../exception/ResultCode.java` |
| 2 | 创建 SPU DTO/VO + Service + Controller（含事务多表写入） | `mall-product/.../spu/` |
| 3 | 创建 HTTP 调试文件 | `http/product-spu-demo.http` |
