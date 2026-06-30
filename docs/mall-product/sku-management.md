# SKU 管理 - 需求与接口文档

> 模块：`mall-product`
> 表：`pms_sku_info`、`pms_sku_images`、`pms_sku_sale_attr_value`
> 版本：v1.0
> 更新时间：2026-06-27

---

## 一、业务背景

SKU（Stock Keeping Unit）是可售卖、可库存的最小单位，对应一种具体的规格组合。SKU 由 SPU + 销售属性组合派生，有独立的价格、库存、销量、图片。

SKU 依赖 SPU 存在，`category_id` / `brand_id` 冗余自 SPU，便于按分类/品牌直接查 SKU（避免回联 SPU 表）。

> **概念详解**：SPU/SKU/属性的概念与实体关系见 [商品中心概述](./overview.md)。

SKU 数据被以下模块引用：

| 引用模块 | 表 | 字段 | 说明 |
|---------|---|------|------|
| 购物车 | Redis Hash | `skuId` | 购物车按 SKU 维度记录 |
| 订单 | `oms_order_item` | `sku_id` | 下单按 SKU 维度 |
| 库存 | `wms_ware_sku` | `sku_id` | 库存按 SKU 维度扣减 |
| 检索 | OpenSearch | `skuId` | 上架 SKU 同步到搜索引擎 |
| 秒杀 | `sms_seckill_sku_relation` | `sku_id` | 秒杀活动关联 SKU |

SKU 的变更（删除、价格调整）必须评估对以上模块的影响。

---

## 二、功能需求

### 2.1 功能清单

| 编号 | 功能 | 说明 |
|------|------|------|
| D1 | SKU 分页查询 | 按 SPU 查询其下 SKU |
| D2 | SKU 详情 | 含图片集、销售属性值 |
| D3 | 新增 SKU | 校验销售属性值组合唯一，事务写入 |
| D4 | 修改 SKU | 同步更新销售属性值与图片集 |
| D5 | 删除 SKU | 逻辑删除级联从属表 |

### 2.2 业务规则

#### D1 SKU 分页查询

- 按 `spu_id` 查询其下 SKU
- 仅返回 `is_deleted = 0` 的 SKU
- 按 `id` 升序排列

#### D2 SKU 详情

- 返回主表字段 + 图片集（`pms_sku_images`，按 `img_sort` 升序）+ 销售属性值（`pms_sku_sale_attr_value`）

#### D3 新增 SKU

- `spu_id` 必须存在；`category_id` / `brand_id` 冗余自 SPU，由后端填充，前端不传
- **组合唯一性校验**：销售属性值组合（`attr_id` → `attr_value` 集合）在同一 SPU 下**必须唯一**，否则抛 `SKU_ATTR_COMBO_DUPLICATE`
- 销售属性值：仅 `attr_type ∈ {0,2}` 的属性可用；`attr_id` 必须属于 SPU 分类下的销售属性
- `price` 必须 > 0；`sku_default_img` 必填（若无单独默认图，取 `pms_sku_images` 中 `default_img=1` 的图）
- 主表 + 图片集 + 销售属性值，**同一事务**写入
- 审计字段（`create_by` / `update_by`）由 `MyMetaObjectHandler` 自动填充

#### D4 修改 SKU

- `id` 必填，校验 SKU 存在
- 销售属性值与图片集采用**全量覆盖**（逻辑删旧 + 插新）
- 主表字段 `updateById`（携带 `version` 乐观锁）
- 销售属性值组合变更时重新校验唯一性

#### D5 删除 SKU

- **引用评估**：库存/订单引用为跨服务判断，由 ware/order 服务提供校验接口（待实现）
- **级联逻辑删除**：`pms_sku_info` + `pms_sku_images` + `pms_sku_sale_attr_value`（同事务）
- 删除后从 OpenSearch 移除

---

## 三、数据模型

### 3.1 SKU 三表

| 表 | 关键字段 | 设计要点 |
|----|---------|---------|
| `pms_sku_info` | `spu_id`、`category_id`、`brand_id`、`price`、`sale_count` | 冗余分类/品牌便于直查；索引 `idx_spu_id` / `idx_category_id` / `idx_brand_id` |
| `pms_sku_images` | `sku_id`、`img_url`、`default_img` | SKU 图片集 |
| `pms_sku_sale_attr_value` | `sku_id`、`attr_id`、`attr_name`、`attr_value` | 销售属性值，冗余 `attr_name`；索引 `idx_sku_id` / `idx_attr_id` |

### 3.2 索引补充建议

- `pms_sku_sale_attr_value`：建议加 `uk_sku_attr(sku_id, attr_id)`，保证一个 SKU 在同一销售属性上只取一个值
- 逻辑删除与唯一约束冲突的处理思路同 [品牌管理](./brand-management.md)

---

## 四、接口设计

### 4.1 接口总览

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| SKU 分页查询 | GET | `/product/sku/list` | 按 SPU |
| SKU 详情 | GET | `/product/sku/{id}` | 含图片/销售属性值 |
| 新增 SKU | POST | `/product/sku` | 校验组合唯一 |
| 修改 SKU | PUT | `/product/sku` | 全量覆盖从属 |
| 删除 SKU | DELETE | `/product/sku/{id}` | 级联逻辑删除 |

> 所有接口经网关，前端 `baseUrl=/api`，网关将 `/api/product/**` 路由到 `mall-product`（`StripPrefix=1`），与品牌/分类接口共用 `product-route`。

### 4.2 新增 SKU

**POST** `/product/sku`

**请求体**（`SkuSaveDTO`）：

```json
{
    "spuId": 1001,
    "skuName": "iPhone 15 256G 钛蓝色",
    "price": 5999.00,
    "skuTitle": "Apple iPhone 15 256G 钛蓝色",
    "skuSubtitle": "限时优惠",
    "skuDefaultImg": "https://oss.example.com/sku/1.jpg",
    "images": [
        { "imgUrl": "https://oss.example.com/sku/1.jpg", "imgSort": 0, "defaultImg": 1 }
    ],
    "saleAttrs": [
        { "attrId": 2001, "attrName": "颜色", "attrValue": "钛蓝色" },
        { "attrId": 2002, "attrName": "版本", "attrValue": "256G" }
    ]
}
```

**业务逻辑**：

```
1. 查 SPU，不存在抛 SPU_NOT_FOUND；取 category_id / brand_id 冗余写入 SKU
2. 校验 saleAttrs 中每个 attrId 属于该分类且 attr_type ∈ {0,2}
3. 组合唯一性校验：该 SPU 下已存在相同销售属性值组合的 SKU → 抛 SKU_ATTR_COMBO_DUPLICATE
4. 插入 pms_sku_info
5. 批量插入 pms_sku_images
6. 批量插入 pms_sku_sale_attr_value（冗余 attr_name）
   （4~6 同一事务 @Transactional）
```

> 其余接口（SKU 修改/删除）体例与品牌接口一致，实现时按 [品牌管理](./brand-management.md) 的模板补全。

---

## 五、DTO / VO 定义

```
com.mymall.product.dto.spu/
├── SkuSaveDTO.java              // SKU 新增/修改
├── SkuImageDTO.java             // SKU 图片项
└── SkuSaleAttrValueDTO.java     // 销售属性值项

com.mymall.product.vo/
└── SkuVO.java                   // SKU 详情（含图片/销售属性）
```

---

## 六、错误码

在 `ResultCode` 中补充 SKU 相关错误码（**54030~54033 码段**）：

| 错误码 | 枚举值 | 消息 | 触发场景 |
|--------|--------|------|---------|
| 54030 | `SKU_NOT_FOUND` | SKU 不存在 | ID 查询不到 |
| 54031 | `SKU_ATTR_COMBO_DUPLICATE` | 同一商品下销售属性组合已存在 | SKU 组合唯一性冲突 |
| 54032 | `SKU_SALE_ATTR_INVALID` | 销售属性不属于该分类或类型不匹配 | `saleAttrs` 校验失败 |
| 54033 | `SKU_PRICE_INVALID` | SKU 价格非法 | `price` ≤ 0 |

对应枚举（追加到 `ResultCode`）：

```java
// ==================== 商品 SKU 54030+ ====================
SKU_NOT_FOUND(54030, "SKU 不存在"),
SKU_ATTR_COMBO_DUPLICATE(54031, "同一商品下销售属性组合已存在"),
SKU_SALE_ATTR_INVALID(54032, "销售属性不属于该分类或类型不匹配"),
SKU_PRICE_INVALID(54033, "SKU 价格非法"),
```

> 同时在 `ResultCode` 头部码段规划注释中补：`54030~54033 - 商品 SKU`。

---

## 七、网关路由

与分类/品牌/属性/SPU 接口共用 `product-route` 路由，详见 [品牌管理](./brand-management.md)。

---

## 八、HTTP 调试文件

```
http/product-sku-demo.http
```

---

## 九、非功能性要求

> 模块级非功能性要求（性能、缓存、事务、检索同步等）见 [商品中心概述](./overview.md)。

---

## 十、实现清单

| 序号 | 任务 | 文件 |
|------|------|------|
| 1 | 补充 ResultCode SKU 错误码 + 码段注释 | `mall-common/.../exception/ResultCode.java` |
| 2 | 创建 SKU DTO/VO + Service + Controller（含组合唯一性校验） | `mall-product/.../sku/` |
| 3 | 补充索引 SQL（`uk_sku_attr`） | `init/mysql/mymall_pms.sql` |
| 4 | 创建 HTTP 调试文件 | `http/product-sku-demo.http` |
