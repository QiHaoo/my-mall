# 属性管理 - 需求与接口文档

> 模块：`mall-product`
> 表：`pms_attr`
> 版本：v1.0
> 更新时间：2026-06-29

---

## 一、业务背景

属性是商品中心最核心的基础数据之一，决定商品"长什么样"和"怎么选"。属性分两类：

- **规格参数（基本属性）**：描述商品固有特征，挂在 SPU 上，用于详情页参数表和检索筛选（如屏幕尺寸、CPU）。
- **销售属性**：参与区分 SKU，不同取值组合产生不同 SKU（如颜色、版本）。

两者元数据统一定义在 `pms_attr` 表中，靠 `attr_type` 区分。规格参数可选关联属性分组，用于详情页参数表分区展示；销售属性不参与分组。

> **关联文档**：属性分组的管理见 [属性分组管理](./attrgroup-management.md)。
> **概念详解**：SPU/SKU/属性的概念与实体关系见 [商品中心概述](./overview.md)。

属性数据被以下模块引用：

| 引用模块 | 表 | 字段 | 说明 |
|---------|---|------|------|
| SPU | `pms_product_attr_value` | `attr_id` | SPU 的规格参数取值 |
| SKU | `pms_sku_sale_attr_value` | `attr_id` | SKU 的销售属性取值 |
| 检索 | `pms_attr.search_type` | — | `search_type=1` 的属性同步到 OpenSearch |

属性的变更（删除、改名）必须评估对以上模块的影响：
- 删除属性前检查 `pms_product_attr_value` / `pms_sku_sale_attr_value` 是否有引用
- 属性改名时同步刷新 `pms_product_attr_value.attr_name` 与 `pms_sku_sale_attr_value.attr_name`

---

## 二、核心设计决策

### 2.1 规格参数与销售属性共用接口

`pms_attr` 表统一存储两类属性，靠 `attr_type` 区分。接口层面共用一套 CRUD，通过 query 参数 `attrType` 过滤：

- `GET /product/attr/list?attrType=1` → 规格参数列表
- `GET /product/attr/list?attrType=0` → 销售属性列表
- 新增/详情接口中 `attrType` 作为请求体字段

差异点：
- 规格参数（`attrType=1`）：可选关联属性分组，后端自动维护关联关系
- 销售属性（`attrType=0`）：不参与分组关联，`attrGroupId` 必须为 null

### 2.2 属性-分组关系：业务层强制 1:1

一个基本属性最多归属一个分组。该约束的详细说明、关联表设计、关联管理接口见 [属性分组管理 - 2.1 节](./attrgroup-management.md#21-属性-分组关系表结构-nn业务层强制-11)。

属性管理模块在新增/修改规格参数时，通过调用 `IAttrAttrgroupRelationService.upsertRelation()` 自动维护分组关系（详见 [6.3 新增属性](#63-新增属性)、[6.4 修改属性](#64-修改属性)）。

### 2.3 "查所有"机制：catelogId 可选

属性列表的 `catelogId` 参数为**可选**：
- 传了 → 按该三级分类过滤
- 不传 → 查全部分类（仍分页）

避免 `catelogId=0` 魔数语义。前端逻辑：用户选中三级分类时传 catelogId，未选中时不传。

### 2.4 分类路径（catelogPath）

属性详情需要返回所属分类的完整路径 `[一级id, 二级id, 三级id]`，供前端级联选择器回显。该路径由分类管理模块提供（见 [属性分组管理 - 第十章 分类管理补充](./attrgroup-management.md#十分类管理补充接口)）。

---

## 三、功能需求

### 3.1 功能清单

| 编号 | 功能 | 说明 |
|------|------|------|
| B1 | 属性分页查询 | `attrType` 必传、`catelogId` 可选、`key` 模糊，响应含分组名/分类名 |
| B2 | 属性详情 | 含 `groupId`、`groupName`、`catelogId`、`catelogName`、`catelogPath` |
| B3 | 新增属性 | `attrType`+`icon`+`catelogId` 必填，`attrGroupId` 可选；选了分组自动建关联 |
| B4 | 修改属性 | 分组关系 upsert：无→新增、变了→先删旧再建新、没变→不动 |
| B5 | 批量删除属性 | 校验 `pms_product_attr_value` / `pms_sku_sale_attr_value` 引用 |

> 规格参数、销售属性的管理界面在后台按"选中三级分类 → 展示对应属性"组织。

### 3.2 业务规则

- 属性名在同一分类下唯一（`uk_catelog_name(catelog_id, attr_name, is_deleted)`）
- `attrType` 决定属性归属层：`1`-规格参数、`0`-销售属性、`2`-两者皆是
- `attrType=1` 时 `attrGroupId` 可选；`attrType=0` 时 `attrGroupId` 必须为 null
- `valueSelect` 逗号分隔的可选值；前端录入商品时下拉选择，也可自定义输入
- `searchType=1` 的属性，其值变更需同步到搜索引擎（Canal binlog → MQ → OpenSearch）
- 删除属性前：校验 `pms_product_attr_value`（`attr_type=1/2`）/ `pms_sku_sale_attr_value`（`attr_type=0/2`）是否有引用，有则拒绝
- 属性改名时：同步刷新 `pms_product_attr_value.attr_name` 与 `pms_sku_sale_attr_value.attr_name`
- 新增规格参数时若传 `attrGroupId`，后端在同一事务内写入 `pms_attr_attrgroup_relation`（1:1 校验：该 attr 不能已归属其他分组）
- 修改规格参数时分组关系处理：无关联且传了 groupId→新增；有关联且 groupId 变了→删旧建新；有关联且 groupId 置 null→删除关联；没变→不动

---

## 四、前端设计

> **Figma 设计稿**：[商品中心 / 属性管理](https://www.figma.com/design/UPX2R9RSakc3klzVIEB6Hq/My-mall?node-id=58-320)
>
> 关键 Frame：`AttrBaseList`、`AttrSaleList`、`AttrForm`

### 4.1 规格参数管理页（`/product/attr/base`）

采用「左侧分类树 + 右侧表格」布局（同属性分组管理页）：

```
┌─ 左侧分类树（280px）──────────┬─ 右侧规格参数区 ──────────────────────────┐
│  分类树（三级，同分类管理）     │  工具栏：[属性名输入框] [查询] [新增属性] [批量删除] │
│  点击三级分类节点 → 过滤右侧    │  表格：复选框 | 属性名 | 图标 | 所属分类 | 所属分组 |   │
│  未选中 → 右侧展示所有属性      │         可选值 | 检索 | 快速展示 | 启用 | 操作      │
│                              │  操作：✎编辑 / 🗑删除                            │
│                              │  底部分页                                       │
└──────────────────────────────┴──────────────────────────────────────────────┘
```

**表格列**：
- 复选框 | 属性名 | 图标 | 所属分类 | 所属分组 | 可选值 | 检索 | 快速展示 | 启用 | 操作

**属性表单弹窗**（新增/编辑复用，560×680）：
- 属性名（必填）
- 属性图标（必填，URL 输入）
- 所属分类（必填，el-tree-select 三级）
- 所属分组（可选，el-select 下拉，选项为该分类下的分组；销售属性新增时此字段隐藏）
- 属性类型（必填，el-select：规格参数/销售属性/两者皆是；销售属性新增页固定为"销售属性"且不可改）
- 值类型（单值/多选）
- 可选值（动态 tag 输入，逗号分隔存储）
- 检索（switch：是否参与检索筛选）
- 快速展示（switch：详情页参数区置顶）
- 启用（switch）

### 4.2 销售属性管理页（`/product/attr/sale`）

与规格参数管理页共用组件，区别：
- 列表请求固定 `attrType=0`
- 新增表单固定 `attrType=0` 且不可修改，隐藏「所属分组」字段
- 表格不展示「所属分组」列

### 4.3 组件拆分

| 组件 | 职责 | 消费接口 |
|------|------|---------|
| AttrList | 属性列表页主体（规格参数/销售属性复用） | GET /product/attr/list，DELETE /product/attr/batch |
| AttrForm | 新增/编辑属性表单弹窗 | POST/PUT /product/attr，GET /product/attr/{id} |
| CategoryTreeSelect | 三级分类树选择器（左侧树+表单内 select） | GET /product/category/tree |

### 4.4 关键交互

- **分类过滤**：左侧分类树点击三级分类 → 右侧表格带 `catelogId` 重新查询；未选中则不带 `catelogId`（查所有）
- **多字段模糊搜索**：`key` 参数同时对属性名/可选值模糊匹配
- **属性类型切换**：新增规格参数页 `attrType=1` 固定不可改；新增销售属性页 `attrType=0` 固定不可改；编辑页可改但需谨慎（改 type 影响属性值归属层）
- **分组关系联动**：规格参数表单中选了「所属分组」→ 提交时后端自动建关联；改了分组 → 后端 upsert 关联
- **删除后列表刷新**：删除成功后停留在当前页，若当前页已无数据则回退到上一页

---

## 五、数据模型

### 5.1 属性表

| 表 | 关键字段 | 设计要点 |
|----|---------|---------|
| `pms_attr` | `attr_type`、`catelog_id`、`value_select`、`search_type`、`show_desc` | 属性定义按分类归属；`attr_type` 区分基本/销售 |

> 属性与分组的关联表 `pms_attr_attrgroup_relation` 见 [属性分组管理 - 5.3 节](./attrgroup-management.md#53-表结构-pms_attr_attrgroup_relation)。

### 5.2 表结构 `pms_attr`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 主键（BaseEntity） |
| `attr_name` | varchar(64) | 是 | 属性名 |
| `search_type` | tinyint | 是 | 是否检索：0-否，1-是，默认 0 |
| `value_type` | tinyint | 否 | 值类型：0-单值，1-多选，默认 0 |
| `icon` | varchar(512) | 否 | 属性图标 URL |
| `value_select` | varchar(512) | 否 | 可选值列表，逗号分隔 |
| `attr_type` | tinyint | 是 | 属性类型：0-销售，1-基本，2-两者皆是 |
| `enable` | tinyint | 是 | 启用状态：0-禁用，1-启用，默认 1 |
| `catelog_id` | bigint | 是 | 所属分类 ID（三级） |
| `show_desc` | tinyint | 是 | 快速展示：0-否，1-是，默认 0 |
| `create_time` / `update_time` | datetime | 是 | 审计字段（BaseEntity） |
| `create_by` / `update_by` | bigint | 否 | 审计字段（BaseEntity） |
| `is_deleted` | tinyint | 是 | 逻辑删除（`@TableLogic`） |
| `version` | int | 是 | 乐观锁（`@Version`） |

### 5.3 索引

`pms_attr`：

| 索引 | 字段 | 类型 | 说明 |
|------|------|------|------|
| PK | `id` | PRIMARY | 主键 |
| IDX_CATELOG_ID | `catelog_id` | NORMAL | 按分类查属性（已有） |
| IDX_CATELOG_ATTR_TYPE | `catelog_id, attr_type` | NORMAL | 按分类+类型高频查询（补充） |
| UK_CATELOG_NAME | `catelog_id, attr_name, is_deleted` | UNIQUE | 同分类属性名唯一（补充） |

> 生产环境补充索引 SQL：
> ```sql
> ALTER TABLE pms_attr ADD INDEX idx_catelog_attr_type (catelog_id, attr_type);
> ALTER TABLE pms_attr ADD UNIQUE INDEX uk_catelog_name (catelog_id, attr_name, is_deleted);
> ```

---

## 六、接口设计

### 6.1 接口总览

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 属性分页查询 | GET | `/product/attr/list` | `attrType` 必传、`catelogId` 可选、`key` 模糊 |
| 属性详情 | GET | `/product/attr/{id}` | 含 groupId/groupName/catelogPath |
| 新增属性 | POST | `/product/attr` | attrGroupId 可选，选了自动建关联 |
| 修改属性 | PUT | `/product/attr` | 分组关系 upsert |
| 批量删除属性 | DELETE | `/product/attr/batch` | 校验属性值表引用 |

> 所有接口经网关，前端 `baseUrl=/api`，网关将 `/api/product/**` 路由到 `mall-product`（`StripPrefix=1`），与品牌/分类接口共用 `product-route`。

---

### 6.2 属性分页查询

**GET** `/product/attr/list?attrType=1&catelogId=225&key=屏幕&pageNum=1&pageSize=10`

**请求参数**（Query，`AttrQueryDTO`）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `attrType` | Integer | 是 | 0-销售属性，1-规格参数，2-两者皆是 |
| `catelogId` | Long | 否 | 三级分类 ID，不传则查所有分类 |
| `key` | String | 否 | 模糊匹配属性名 + 可选值 |
| `pageNum` | Integer | 否 | 默认 1 |
| `pageSize` | Integer | 否 | 默认 10，最大 100 |

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "records": [
            {
                "id": 10,
                "attrName": "屏幕尺寸",
                "attrType": 1,
                "icon": "https://oss.example.com/icon/screen.png",
                "valueSelect": "5.1,5.5,6.1",
                "valueType": 0,
                "searchType": 1,
                "showDesc": 0,
                "enable": 1,
                "catelogId": 225,
                "catelogName": "手机",
                "groupId": 1,
                "groupName": "主体"
            }
        ],
        "total": 1,
        "current": 1,
        "size": 10,
        "pages": 1
    }
}
```

**实现要点**：
- `LambdaQueryWrapper<Attr>` 拼接条件
- `attrType` 必传，按类型过滤（`attr_type=2` 的属性同时属于规格参数和销售属性）：
  - `attrType=1`（规格参数）→ `in(attrType, 1, 2)`
  - `attrType=0`（销售属性）→ `in(attrType, 0, 2)`
- `catelogId` 非空时 `eq`，为空时不加条件
- `key` 非空时 `like(attrName, key).or().like(valueSelect, key)`
- 排序：`order by sort asc, id asc`
- 响应中 `catelogName`、`groupId`、`groupName` 需补充查询：
  - `catelogName`：批量查 `pms_category`
  - `groupId` / `groupName`：左联 `pms_attr_attrgroup_relation` + `pms_attr_group`（1:1 下每个 attr 最多一条关联）

### 6.3 属性详情

**GET** `/product/attr/{id}`

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "id": 10,
        "attrName": "屏幕尺寸",
        "attrType": 1,
        "icon": "https://oss.example.com/icon/screen.png",
        "valueSelect": "5.1,5.5,6.1",
        "valueType": 0,
        "searchType": 1,
        "showDesc": 0,
        "enable": 1,
        "catelogId": 225,
        "catelogName": "手机",
        "catelogPath": [1, 37, 225],
        "groupId": 1,
        "groupName": "主体"
    }
}
```

**实现要点**：
- 查属性主记录，不存在抛 `ATTR_NOT_FOUND`
- `catelogName`：查 `pms_category`
- `catelogPath`：调用 `ICategoryService.getCatelogPath(catelogId)`
- `groupId` / `groupName`：查 `pms_attr_attrgroup_relation`（1:1 最多一条）+ `pms_attr_group`
- 销售属性（`attrType=0`）的 `groupId` / `groupName` 为 null

### 6.4 新增属性

**POST** `/product/attr`

**请求体**（`AttrSaveDTO`，校验分组 `Create.class`）：

```json
{
    "attrName": "屏幕尺寸",
    "attrType": 1,
    "icon": "https://oss.example.com/icon/screen.png",
    "valueSelect": "5.1,5.5,6.1",
    "valueType": 0,
    "searchType": 1,
    "showDesc": 0,
    "enable": 1,
    "catelogId": 225,
    "attrGroupId": 1
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `id` | Long | 修改时必填 | `@NotNull(groups = Update.class)` |
| `attrName` | String | 是 | `@NotBlank`，最大 64 字符 |
| `attrType` | Integer | 是 | `@NotNull`，0/1/2 |
| `icon` | String | 是 | `@NotBlank`，最大 512 字符 |
| `valueSelect` | String | 否 | 最大 512 字符 |
| `valueType` | Integer | 否 | 0-单值，1-多选，默认 0 |
| `searchType` | Integer | 否 | 0-否，1-是，默认 0 |
| `showDesc` | Integer | 否 | 0-否，1-是，默认 0 |
| `enable` | Integer | 否 | 0-禁用，1-启用，默认 1 |
| `catelogId` | Long | 是 | `@NotNull`，需为三级分类 |
| `attrGroupId` | Long | 否 | 所属分组；`attrType=0` 时必须为 null |
| `version` | Integer | 修改时必填 | 乐观锁版本号 |

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// 属性名重复
{ "code": 54002, "msg": "同分类下属性名已存在", "data": null }

// 分类不存在或非三级
{ "code": 54013, "msg": "分类不存在或非三级分类", "data": null }
```

**业务逻辑**：

```
1. 校验分类存在且 cat_level = 3，否则抛 ATTR_CATELOG_INVALID
2. 校验同分类下属性名唯一（排除逻辑删除），重复抛 ATTR_NAME_DUPLICATE
3. attrType=0 时若 attrGroupId 非 null，抛 ATTR_TYPE_INVALID（销售属性不可关联分组）
4. 插入 pms_attr（审计字段自动填充）
5. 若 attrGroupId 非空（仅 attrType=1/2）：
   a. 1:1 校验：该 attr 不能已归属其他分组（新增场景下 attr 刚创建，必然无关联，可省略）
   b. 校验 attrGroupId 所属分类与 attr.catelogId 一致
   c. 调用 IAttrAttrgroupRelationService.upsertRelation(attrId, attrGroupId) 建立关联
   （第 4、5 步在同一事务 @Transactional）
```

### 6.5 修改属性

**PUT** `/product/attr`

**请求体**（`AttrSaveDTO`，校验分组 `Update.class`）：

```json
{
    "id": 10,
    "attrName": "屏幕尺寸",
    "attrType": 1,
    "icon": "https://oss.example.com/icon/screen.png",
    "valueSelect": "5.1,5.5,6.1,6.7",
    "valueType": 0,
    "searchType": 1,
    "showDesc": 1,
    "enable": 1,
    "catelogId": 225,
    "attrGroupId": 2,
    "version": 0
}
```

**业务逻辑**：

```
1. 查询属性，不存在抛 ATTR_NOT_FOUND
2. 属性名变更时校验同分类唯一（排除自身 + 逻辑删除）
3. catelogId 变更时校验新分类为三级分类
4. attrType=0 时若 attrGroupId 非 null，抛 ATTR_TYPE_INVALID
5. updateById（携带 version 触发乐观锁）
6. 分组关系处理（仅 attrType=1/2）：调用 IAttrAttrgroupRelationService.upsertRelation(attrId, attrGroupId)
   （第 5、6 步在同一事务 @Transactional）
7. 属性名变更时同步刷新冗余字段：
   - UPDATE pms_product_attr_value SET attr_name=? WHERE attr_id=? AND is_deleted=0
   - UPDATE pms_sku_sale_attr_value SET attr_name=? WHERE attr_id=? AND is_deleted=0
```

### 6.6 批量删除属性

**DELETE** `/product/attr/batch`

**请求体**（`AttrBatchDeleteDTO`）：

```json
{
    "ids": [10, 11, 12]
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `ids` | `List<Long>` | 是 | `@NotEmpty`，每个元素 `@NotNull` |

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// 有属性值引用，拒绝
{ "code": 54003, "msg": "属性 [屏幕尺寸] 已被商品引用，无法删除", "data": null }

// id 列表为空
{ "code": 54005, "msg": "批量删除 id 列表不能为空", "data": null }
```

**业务逻辑**：

```
1. ids 为空抛 ATTR_BATCH_DELETE_EMPTY
2. 逐个检查引用：
   - attrType=1/2：SELECT count(*) FROM pms_product_attr_value
     WHERE attr_id=? AND is_deleted=0
   - attrType=0/2：SELECT count(*) FROM pms_sku_sale_attr_value
     WHERE attr_id=? AND is_deleted=0
   - 任一 > 0 → 抛 ATTR_HAS_REFERENCE，携带该属性名（整体事务回滚）
3. 全部通过后：
   a. 逻辑删除属性：UPDATE pms_attr SET is_deleted=1 WHERE id IN (ids)
   b. 逻辑删除关联：UPDATE pms_attr_attrgroup_relation SET is_deleted=1
      WHERE attr_id IN (ids)
   （整个流程在同一事务 @Transactional）
```

---

## 七、DTO / VO 定义

```
com.mymall.product.dto.attr/
├── AttrSaveDTO.java                   // 属性新增/修改（含 attrType/attrGroupId）
├── AttrQueryDTO.java                  // 属性分页查询（继承 PageQuery）
└── AttrBatchDeleteDTO.java            // 批量删除属性

com.mymall.product.vo.attr/
├── AttrVO.java                        // 属性详情（含 groupId/groupName/catelogName/catelogPath）
└── AttrListVO.java                    // 属性列表项（含 catelogName/groupId/groupName）
```

### 7.1 AttrSaveDTO

```java
@Data
@Schema(description = "新增/修改属性")
public class AttrSaveDTO {

    @NotNull(groups = Update.class, message = "属性ID不能为空")
    @Schema(description = "属性ID（修改时必填）")
    private Long id;

    @NotBlank(message = "属性名不能为空")
    @Size(max = 64, message = "属性名最长 64 字符")
    @Schema(description = "属性名", example = "屏幕尺寸")
    private String attrName;

    @NotNull(message = "属性类型不能为空")
    @Min(0) @Max(2)
    @Schema(description = "属性类型：0-销售 1-基本 2-两者皆是")
    private Integer attrType;

    @NotBlank(message = "属性图标不能为空")
    @Size(max = 512, message = "图标地址最长 512 字符")
    @Schema(description = "属性图标 URL")
    private String icon;

    @Size(max = 512, message = "可选值最长 512 字符")
    @Schema(description = "可选值列表，逗号分隔")
    private String valueSelect;

    @Schema(description = "值类型：0-单值 1-多选")
    private Integer valueType;

    @Schema(description = "是否检索：0-否 1-是")
    private Integer searchType;

    @Schema(description = "快速展示：0-否 1-是")
    private Integer showDesc;

    @Schema(description = "启用状态：0-禁用 1-启用")
    private Integer enable;

    @NotNull(message = "所属分类ID不能为空")
    @Schema(description = "所属三级分类ID")
    private Long catelogId;

    @Schema(description = "所属分组ID（attrType=0 时必须为 null）")
    private Long attrGroupId;

    @NotNull(groups = Update.class, message = "版本号不能为空")
    @Schema(description = "乐观锁版本号（修改时必填）")
    private Integer version;
}
```

### 7.2 AttrQueryDTO

```java
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "属性分页查询条件")
public class AttrQueryDTO extends PageQuery {

    @NotNull(message = "属性类型不能为空")
    @Min(0) @Max(2)
    @Schema(description = "属性类型：0-销售 1-基本 2-两者皆是")
    private Integer attrType;

    @Schema(description = "三级分类ID（不传则查所有分类）")
    private Long catelogId;

    @Schema(description = "模糊匹配属性名 + 可选值")
    private String key;
}
```

### 7.3 AttrBatchDeleteDTO

```java
@Data
@Schema(description = "批量删除属性")
public class AttrBatchDeleteDTO {

    @NotEmpty(message = "id列表不能为空")
    @Schema(description = "属性ID列表")
    private List<Long> ids;
}
```

### 7.4 AttrVO

```java
@Data
@Schema(description = "属性详情")
public class AttrVO {
    private Long id;
    private String attrName;
    private Integer attrType;
    private String icon;
    private String valueSelect;
    private Integer valueType;
    private Integer searchType;
    private Integer showDesc;
    private Integer enable;
    private Long catelogId;
    private String catelogName;
    /** 分类完整路径：[一级id, 二级id, 三级id] */
    private List<Long> catelogPath;
    /** 所属分组ID（销售属性为 null） */
    private Long groupId;
    /** 所属分组名（销售属性为 null） */
    private String groupName;
}
```

### 7.5 AttrListVO

```java
@Data
@Schema(description = "属性列表项")
public class AttrListVO {
    private Long id;
    private String attrName;
    private Integer attrType;
    private String icon;
    private String valueSelect;
    private Integer valueType;
    private Integer searchType;
    private Integer showDesc;
    private Integer enable;
    private Long catelogId;
    private String catelogName;
    private Long groupId;
    private String groupName;
}
```

---

## 八、Service 接口设计

### 8.1 IAttrService

```java
public interface IAttrService extends IService<Attr> {

    /** 属性分页查询（attrType 必传，catelogId 可选） */
    PageVO<AttrListVO> pageQuery(AttrQueryDTO query);

    /** 属性详情（含 catelogName + catelogPath + groupId + groupName） */
    AttrVO getAttrDetail(Long id);

    /** 新增属性（若传 attrGroupId 自动建关联） */
    void saveAttr(AttrSaveDTO dto);

    /** 修改属性（分组关系 upsert + 改名同步刷新冗余） */
    void updateAttr(AttrSaveDTO dto);

    /** 批量删除属性（校验属性值表引用） */
    void batchDelete(AttrBatchDeleteDTO dto);
}
```

> 分组关系的维护通过注入 `IAttrAttrgroupRelationService` 并调用其 `upsertRelation` 方法实现，接口定义见 [属性分组管理 - 8.2 节](./attrgroup-management.md#82-iattrattrgrouprelationservice)。

---

## 九、错误码

在 `ResultCode` 中补充属性相关错误码（**54001~54005 码段**）：

| 错误码 | 枚举值 | 消息 | 触发场景 |
|--------|--------|------|---------|
| 54001 | `ATTR_NOT_FOUND` | 属性不存在 | ID 查询不到 |
| 54002 | `ATTR_NAME_DUPLICATE` | 同分类下属性名已存在 | 新增/改名 |
| 54003 | `ATTR_HAS_REFERENCE` | 属性已被商品引用，无法删除 | 删除时属性值表有引用 |
| 54004 | `ATTR_TYPE_INVALID` | 属性类型非法 | `attr_type` 非 0/1/2，或销售属性传了 attrGroupId |
| 54005 | `ATTR_BATCH_DELETE_EMPTY` | 批量删除 id 列表不能为空 | `ids` 为空 |

对应枚举（追加到 `ResultCode`）：

```java
// ==================== 商品属性 54001+ ====================
ATTR_NOT_FOUND(54001, "属性不存在"),
ATTR_NAME_DUPLICATE(54002, "同分类下属性名已存在"),
ATTR_HAS_REFERENCE(54003, "属性已被商品引用，无法删除"),
ATTR_TYPE_INVALID(54004, "属性类型非法"),
ATTR_BATCH_DELETE_EMPTY(54005, "批量删除 id 列表不能为空"),
```

> `ATTR_CATELOG_INVALID`（54013）为属性分组与属性共用错误码，定义在 [属性分组管理 - 九、错误码](./attrgroup-management.md#九错误码)。
> 同时在 `ResultCode` 头部码段规划注释中补：`54001~54019 - 商品属性/属性分组`。

---

## 十、Attr 实体类型对齐

当前 [Attr.java](file:///d:/WorkSpace/my-mall/mall-product/src/main/java/com/mymall/product/entity/Attr.java) 部分字段类型与品牌管理的 Integer 风格不一致：

| 字段 | 当前类型 | 调整为 | 说明 |
|------|---------|--------|------|
| `searchType` | `Byte` | `Integer` | 与 `showStatus` 风格一致 |
| `valueType` | `Byte` | `Integer` | 同上 |
| `attrType` | `Byte` | `Integer` | 同上 |
| `showDesc` | `Byte` | `Integer` | 同上 |
| `enable` | `Long` | `Integer` | `Long` 明显是笔误 |

> 服务实现还是空壳，改动零风险。DB 层 `tinyint` MyBatis-Plus 自动映射 Integer 无额外配置。

---

## 十一、网关路由

与分类/品牌接口共用 `product-route` 路由，详见 [品牌管理 - 网关路由](./brand-management.md#八网关路由)。

---

## 十二、HTTP 调试文件

`http/product-attr-demo.http`

```http
### ==================== 规格参数 / 销售属性 ====================

### 1. 规格参数分页查询
GET http://localhost:1000/api/product/attr/list?attrType=1&catelogId=225&pageNum=1&pageSize=10

### 2. 销售属性分页查询
GET http://localhost:1000/api/product/attr/list?attrType=0&pageNum=1&pageSize=10

### 3. 属性详情
GET http://localhost:1000/api/product/attr/10

### 4. 新增规格参数（含分组）
POST http://localhost:1000/api/product/attr
Content-Type: application/json

{
    "attrName": "屏幕尺寸",
    "attrType": 1,
    "icon": "https://oss.example.com/icon/screen.png",
    "valueSelect": "5.1,5.5,6.1",
    "valueType": 0,
    "searchType": 1,
    "showDesc": 0,
    "enable": 1,
    "catelogId": 225,
    "attrGroupId": 1
}

### 5. 新增销售属性（无分组）
POST http://localhost:1000/api/product/attr
Content-Type: application/json

{
    "attrName": "颜色",
    "attrType": 0,
    "icon": "https://oss.example.com/icon/color.png",
    "valueSelect": "钛蓝色,粉色,黑色",
    "valueType": 0,
    "searchType": 1,
    "showDesc": 0,
    "enable": 1,
    "catelogId": 225
}

### 6. 修改属性（切换分组）
PUT http://localhost:1000/api/product/attr
Content-Type: application/json

{
    "id": 10,
    "attrName": "屏幕尺寸",
    "attrType": 1,
    "icon": "https://oss.example.com/icon/screen.png",
    "valueSelect": "5.1,5.5,6.1,6.7",
    "valueType": 0,
    "searchType": 1,
    "showDesc": 1,
    "enable": 1,
    "catelogId": 225,
    "attrGroupId": 2,
    "version": 0
}

### 7. 批量删除属性
DELETE http://localhost:1000/api/product/attr/batch
Content-Type: application/json

{
    "ids": [10, 11]
}
```

---

## 十三、非功能性要求

> 模块级非功能性要求（性能、缓存、事务、检索同步等）见 [商品中心概述](./overview.md)。

| 项目 | 要求 |
|------|------|
| 性能 | 分页查询 < 200ms（命中 `idx_catelog_id` / `idx_catelog_attr_type`）；详情聚合查询 < 300ms |
| 并发 | 属性为低频写操作，乐观锁（`@Version`）即可 |
| 缓存 | 属性定义按分类缓存到 Redis（TTL 30 分钟），写操作后主动失效 |
| 事务 | 新增属性跨 `pms_attr` + `pms_attr_attrgroup_relation` 双表，修改属性跨 3 表（含冗余刷新），必须 `@Transactional` |
| 检索同步 | `search_type=1` 的属性值通过 Canal → MQ → OpenSearch 同步 |
| 一致性 | 属性改名 → 同步刷新 `pms_product_attr_value.attr_name` / `pms_sku_sale_attr_value.attr_name` |
| 安全 | 管理接口需管理员权限（网关 JWT 鉴权，待实现） |
| 日志 | 写操作记录操作人 + 变更内容；查询不记录 |
| 幂等 | 新增靠同分类名唯一约束兜底 |

---

## 十四、实现清单

| 序号 | 任务 | 文件 |
|------|------|------|
| 1 | 补充 ResultCode 属性错误码（54001~54005）+ 码段注释 | `mall-common/.../exception/ResultCode.java` |
| 2 | Attr 实体字段类型对齐（Byte→Integer，Long→Integer） | `mall-product/.../entity/Attr.java` |
| 3 | 创建属性 DTO/VO（3 个 DTO + 2 个 VO） | `mall-product/.../dto/attr/` + `.../vo/attr/` |
| 4 | 扩展 IAttrService + 实现 AttrServiceImpl（含分组关系处理 + 改名刷新冗余） | `mall-product/.../service/` |
| 5 | 创建 AttrController（属性 CRUD 5 个接口） | `mall-product/.../controller/AttrController.java` |
| 6 | 创建 HTTP 调试文件 | `http/product-attr-demo.http` |
| 7 | 补充索引 SQL（uk_catelog_name / idx_catelog_attr_type） | `init/mysql/mymall_pms.sql` |
