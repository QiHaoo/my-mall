# 属性管理 - 需求与接口文档

> 模块：`mall-product`
> 表：`pms_attr`、`pms_attr_group`、`pms_attr_attrgroup_relation`
> 版本：v1.0
> 更新时间：2026-06-27

---

## 一、业务背景

属性是商品中心最核心的基础数据之一，决定商品"长什么样"和"怎么选"。属性分两类：

- **规格参数（基本属性）**：描述商品固有特征，挂在 SPU 上，用于详情页参数表和检索筛选（如屏幕尺寸、CPU）。
- **销售属性**：参与区分 SKU，不同取值组合产生不同 SKU（如颜色、版本）。

两者元数据统一定义在 `pms_attr` 表中，靠 `attr_type` 区分。属性分组（`pms_attr_group`）仅组织基本属性，用于详情页参数表分区展示。

> **概念详解**：SPU/SKU/属性的概念与实体关系见 [商品中心概述](./overview.md)。

属性数据被以下模块引用：

| 引用模块 | 表 | 字段 | 说明 |
|---------|---|------|------|
| SPU | `pms_product_attr_value` | `attr_id` | SPU 的规格参数取值 |
| SKU | `pms_sku_sale_attr_value` | `attr_id` | SKU 的销售属性取值 |
| 检索 | `pms_attr.search_type` | — | `search_type=1` 的属性同步到 OpenSearch |

属性的变更（删除、改名）必须评估对以上模块的影响。

---

## 二、功能需求

### 2.1 功能清单

| 编号 | 功能 | 说明 |
|------|------|------|
| A1 | 属性分组分页查询 | 按三级分类查询属性分组，支持分组名模糊、分页 |
| A2 | 属性分组详情/新增/修改/删除 | 标准 CRUD，删除前校验组下是否有属性 |
| A3 | 分组-属性关联管理 | 查询分组下属性、为分组添加/移除属性 |
| B1 | 规格参数（基本属性）分页查询 | 按分类查询基本属性，支持 `attr_type` 过滤 |
| B2 | 规格参数详情/新增/修改/删除 | 标准 CRUD，删除前校验是否被 SPU 引用 |
| B3 | 销售属性分页查询 | 按分类查询销售属性 |
| B4 | 销售属性详情/新增/修改/删除 | 与规格参数共用 `pms_attr`，`attr_type=0` |

> 属性分组、规格参数、销售属性的管理界面在后台通常按"选中三级分类 → 展示对应分组/属性"组织。

### 2.2 业务规则

#### 属性分组（A1~A3）

- 分组名在同一分类下唯一（业务层校验，建议加 `uk_catelog_name(catelog_id, attr_group_name, is_deleted)`）
- `catelog_id` 必须为存在的三级分类（`cat_level = 3`）
- 删除分组前：若 `pms_attr_attrgroup_relation` 仍有该分组的关联记录，先解除关联或拒绝删除
- 分组下属性查询：联表 `pms_attr_attrgroup_relation` + `pms_attr`，按 `attr_sort` 升序

#### 规格参数 / 销售属性（B1~B4）

- 属性名在同一分类下唯一（业务层校验）
- `attr_type` 决定属性归属层：`1`-仅基本、`0`-仅销售、`2`-两者皆是
- `value_select` 逗号分隔的可选值；前端录入商品时下拉选择，也可自定义输入
- `search_type=1` 的属性，其值变更需同步到搜索引擎（Canal binlog → MQ → OpenSearch）
- 删除属性前：校验 `pms_product_attr_value` / `pms_sku_sale_attr_value` 是否有引用，有则拒绝
- 属性改名时：同步刷新 `pms_product_attr_value.attr_name` 与 `pms_sku_sale_attr_value.attr_name`

---

## 三、数据模型

### 3.1 属性元数据三表

| 表 | 关键字段 | 设计要点 |
|----|---------|---------|
| `pms_attr` | `attr_type`、`catelog_id`、`value_select`、`search_type`、`show_desc` | 属性定义按分类归属；`attr_type` 区分基本/销售 |
| `pms_attr_group` | `attr_group_name`、`catelog_id`、`sort` | 分组按分类归属，仅组织基本属性 |
| `pms_attr_attrgroup_relation` | `attr_id`、`attr_group_id`、`attr_sort` | 多对多关联；`uk_attr_group(attr_id, attr_group_id)` 防重复 |

### 3.2 索引补充建议

- `pms_attr`：建议加 `idx_catelog_attr_type(catelog_id, attr_type)`，支撑"按分类查基本属性/销售属性"高频查询
- 逻辑删除与唯一约束冲突的处理思路同 [品牌管理](./brand-management.md)

---

## 四、接口设计

### 4.1 接口总览

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 分组分页查询 | GET | `/product/attrgroup/list` | 按三级分类 + 分组名模糊分页 |
| 分组详情 | GET | `/product/attrgroup/{id}` | 含组下属性列表 |
| 新增分组 | POST | `/product/attrgroup` | |
| 修改分组 | PUT | `/product/attrgroup` | |
| 删除分组 | DELETE | `/product/attrgroup/{id}` | 校验组下属性 |
| 分组下属性 | GET | `/product/attrgroup/{id}/attrs` | 联表查询 |
| 添加分组属性 | POST | `/product/attrgroup/relation` | 批量关联 |
| 移除分组属性 | DELETE | `/product/attrgroup/relation` | 批量解联 |
| 规格参数分页 | GET | `/product/attr/base/list` | `attr_type=1/2`，按分类 |
| 销售属性分页 | GET | `/product/attr/sale/list` | `attr_type=0/2`，按分类 |
| 属性详情 | GET | `/product/attr/{id}` | |
| 新增属性 | POST | `/product/attr` | |
| 修改属性 | PUT | `/product/attr` | 改名同步刷新冗余 |
| 删除属性 | DELETE | `/product/attr/{id}` | 校验引用 |

> 所有接口经网关，前端 `baseUrl=/api`，网关将 `/api/product/**` 路由到 `mall-product`（`StripPrefix=1`），与品牌/分类接口共用 `product-route`。

> 其余接口（分组/属性 CRUD）体例与品牌接口一致，实现时按 [品牌管理](./brand-management.md) 的模板补全。

---

## 五、DTO / VO 定义

```
com.mymall.product.dto.attr/
├── AttrGroupSaveDTO.java        // 属性分组新增/修改
├── AttrGroupQueryDTO.java       // 分组分页查询
├── AttrSaveDTO.java             // 属性新增/修改（含 attr_type）
├── AttrQueryDTO.java            // 属性分页查询
└── AttrRelationDTO.java         // 分组-属性关联（批量）

com.mymall.product.vo/
├── AttrGroupVO.java             // 分组详情（含 attrs）
└── AttrVO.java                  // 属性详情
```

---

## 六、错误码

在 `ResultCode` 中补充属性管理相关错误码（**54001~54012 码段**）：

| 错误码 | 枚举值 | 消息 | 触发场景 |
|--------|--------|------|---------|
| 54001 | `ATTR_NOT_FOUND` | 属性不存在 | ID 查询不到 |
| 54002 | `ATTR_NAME_DUPLICATE` | 同分类下属性名已存在 | 新增/改名 |
| 54003 | `ATTR_HAS_REFERENCE` | 属性已被商品引用，无法删除 | 删除时属性值表有引用 |
| 54004 | `ATTR_TYPE_INVALID` | 属性类型非法 | `attr_type` 非 0/1/2 |
| 54010 | `ATTR_GROUP_NOT_FOUND` | 属性分组不存在 | ID 查询不到 |
| 54011 | `ATTR_GROUP_NAME_DUPLICATE` | 同分类下分组名已存在 | 新增/改名 |
| 54012 | `ATTR_GROUP_HAS_ATTRS` | 分组下存在属性，无法删除 | 删除分组时有关联属性 |

对应枚举（追加到 `ResultCode`）：

```java
// ==================== 商品属性 54001+ ====================
ATTR_NOT_FOUND(54001, "属性不存在"),
ATTR_NAME_DUPLICATE(54002, "同分类下属性名已存在"),
ATTR_HAS_REFERENCE(54003, "属性已被商品引用，无法删除"),
ATTR_TYPE_INVALID(54004, "属性类型非法"),
// ==================== 属性分组 54010+ ====================
ATTR_GROUP_NOT_FOUND(54010, "属性分组不存在"),
ATTR_GROUP_NAME_DUPLICATE(54011, "同分类下分组名已存在"),
ATTR_GROUP_HAS_ATTRS(54012, "分组下存在属性，无法删除"),
```

> 同时在 `ResultCode` 头部码段规划注释中补：`54001~54012 - 商品属性/属性分组`。

---

## 七、网关路由

与分类/品牌/SPU/SKU 接口共用 `product-route` 路由，详见 [品牌管理](./brand-management.md)。

---

## 八、HTTP 调试文件

```
http/product-attr-demo.http
```

---

## 九、非功能性要求

> 模块级非功能性要求（性能、缓存、事务、检索同步等）见 [商品中心概述](./overview.md)。

---

## 十、实现清单

| 序号 | 任务 | 文件 |
|------|------|------|
| 1 | 补充 ResultCode 属性/属性分组错误码 + 码段注释 | `mall-common/.../exception/ResultCode.java` |
| 2 | 创建属性分组 DTO/VO + 实体（已存在）+ Service + Controller | `mall-product/.../attrgroup/` |
| 3 | 创建属性（规格参数/销售属性）DTO/VO + Service + Controller | `mall-product/.../attr/` |
| 4 | 属性改名同步刷新冗余字段（监听属性 update） | `AttrServiceImpl` |
| 5 | 补充索引 SQL（`idx_catelog_attr_type`） | `init/mysql/mymall_pms.sql` |
| 6 | 创建 HTTP 调试文件 | `http/product-attr-demo.http` |
