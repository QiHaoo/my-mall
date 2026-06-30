# 属性分组管理 - 需求与接口文档

> 模块：`mall-product`
> 表：`pms_attr_group`、`pms_attr_attrgroup_relation`
> 版本：v1.0
> 更新时间：2026-06-29

---

## 一、业务背景

属性分组（`pms_attr_group`）用于组织**基本属性（规格参数）**，在商品详情页参数表中按分组分区展示（如"主体"、"屏幕"、"相机"）。每个分组归属于一个三级分类，分组下挂载若干基本属性。

属性分组与属性通过 `pms_attr_attrgroup_relation` 关联表建立关系：

- **表结构支持多对多**（一个分组下多个属性，一个属性也可归入多个分组）
- **业务层强制 1:1**：一个基本属性最多归属一个分组（详见 [2.1 节](#21-属性-分组关系表结构-nn业务层强制-11)）

> **关联文档**：属性（规格参数/销售属性）的管理见 [属性管理](./attr-management.md)。
> **概念详解**：SPU/SKU/属性的概念与实体关系见 [商品中心概述](./overview.md)。

---

## 二、核心设计决策

### 2.1 属性-分组关系：表结构 N:N，业务层强制 1:1

`pms_attr_attrgroup_relation` 表结构支持多对多，但**业务层强制 1:1**：一个基本属性最多归属一个分组。

理由：
- 详情页参数表按分组分区展示，一个属性出现在多个分组会造成展示歧义
- 规格参数详情只需返回单个 `groupId`，前端交互简单
- 修改规格参数时分组关系处理退化为"无→新增 / 有且变了→先删旧再建新"，无需处理多分组冲突

实现约束：
- 新增关联时，若 attr 已归属其他分组，拒绝并提示（错误码 `ATTR_ALREADY_GROUPED`）
- "可关联属性"列表 = 该分类下未归属任何分组的基本属性
- 表结构保留 N:N 不变，约束仅在 Service 层强制

### 2.2 "查所有"机制：categoryId 可选

属性分组列表的 `categoryId` 参数为**可选**：
- 传了 → 按该三级分类过滤
- 不传 → 查全部分类（仍分页）

避免 `categoryId=0` 魔数语义。前端逻辑：用户选中三级分类时传 categoryId，未选中时不传。

### 2.3 分类路径（catelogPath）

属性分组详情需要返回所属分类的完整路径 `[一级id, 二级id, 三级id]`，供前端级联选择器回显。该路径由分类管理模块提供（见 [第十章 分类管理补充](#十分类管理补充接口)）。

---

## 三、功能需求

### 3.1 功能清单

| 编号 | 功能 | 说明 |
|------|------|------|
| A1 | 属性分组分页查询 | `categoryId` 可选过滤、`key` 多字段模糊、分页、排序 |
| A2 | 属性分组详情 | 含 `catelogPath` 数组 |
| A3 | 新增属性分组 | 组名+categoryId 必填，校验三级分类+同级唯一 |
| A4 | 修改属性分组 | 改名校验同级唯一 |
| A5 | 批量删除属性分组 | 校验组下是否有属性关联，有则拒绝 |
| A6 | 查询分组已关联属性 | 联表查询，按 attr_sort 升序 |
| A7 | 查询分组可关联属性 | 该分类下未归属任何分组的基本属性 |
| A8 | 批量新增分组-属性关联 | 1:1 校验，已归属其他分组的拒绝 |
| A9 | 批量移除分组-属性关联 | 逻辑删除关联记录 |

> 属性分组管理界面在后台按"选中三级分类 → 展示对应分组"组织。

### 3.2 业务规则

- 分组名在同一分类下唯一（`uk_category_group_name(category_id, attr_group_name, is_deleted)`）
- `categoryId` 必须为存在的三级分类（`level = 3`）
- 删除分组前：若 `pms_attr_attrgroup_relation` 仍有该分组的关联记录，拒绝删除（错误码 `ATTR_GROUP_HAS_ATTRS`）
- 分组下属性查询（A6）：联表 `pms_attr_attrgroup_relation` + `pms_attr`，按 `attr_sort` 升序
- 可关联属性查询（A7）：该分类下 `attr_type ∈ {1,2}` 且在 `pms_attr_attrgroup_relation` 中**无任何关联记录**的属性
- 批量新增关联（A8）：逐个校验 attr 是否已归属其他分组，已归属则整体拒绝并提示属性名

---

## 四、前端设计

> **Figma 设计稿**：[商品中心 / 属性分组管理](https://www.figma.com/design/UPX2R9RSakc3klzVIEB6Hq/My-mall?node-id=57-151)
>
> 关键 Frame：`AttrGroupList`、`AttrGroupForm`、`AttrGroupRelationDialog`、`AttrRelationSelectDialog`

### 4.1 属性分组管理页（`/product/attrgroup`）

采用「左侧分类树 + 右侧表格」布局：

```
┌─ 左侧分类树（280px）──────────┬─ 右侧属性分组区 ──────────────────────────┐
│  分类树（三级，同分类管理）     │  工具栏：[分组名输入框] [查询] [新增分组] [批量删除] │
│  点击三级分类节点 → 过滤右侧    │  表格：复选框 | 组名 | 所属分类 | 排序 | 图标 | 操作  │
│  未选中 → 右侧展示全部分组      │  操作：✎编辑 / 🔗关联属性 / 🗑删除              │
│                              │  底部分页                                       │
└──────────────────────────────┴──────────────────────────────────────────────┘
```

**工具栏**：
- 分组名输入框（200×32）：模糊搜索组名+描述
- 查询按钮（主色，72×32）
- 新增分组按钮（主色，96×32）
- 批量删除按钮（危险色，96×32）：勾选后启用

**表格列**：
- 复选框列（60px）
- 组名列（180px）
- 所属分类列（200px）：展示三级分类名（如"家用电器/空调"）
- 排序列（100px）：数字，居中
- 图标列（100px）：图标缩略图或"暂无"
- 操作列（260px）：✎编辑 / 🔗关联属性 / 🗑删除（蓝色主色，删除用红色危险色）

**分组表单弹窗**（新增/编辑复用，520×420）：
- 组名（必填，h=40）：单行输入框
- 所属分类（必填，h=40）：el-tree-select 三级分类选择器，编辑时只读
- 排序（h=40，120 宽）：el-input-number，默认 0
- 图标（h=40）：图标 URL 输入框，可选
- 描述（h=60）：textarea，可选

**关联属性弹窗**（520×560）：
- 弹窗头部：标题"关联属性 - {组名}"
- 双区布局：
  - 上区（已关联属性表格）：展示当前分组已关联的属性列表（属性名、图标、可选值、排序、操作-移除）
  - 下区（新增关联区）：「+ 新增关联」按钮 → 弹出属性多选弹窗（展示可关联属性列表，多选确认后批量新增）

### 4.2 组件拆分

| 组件 | 职责 | 消费接口 |
|------|------|---------|
| AttrGroupList | 分组列表页主体：分类树+筛选+表格+分页+批量删除 | GET /product/attrgroup/list，DELETE /product/attrgroup/batch |
| AttrGroupForm | 新增/编辑分组表单弹窗 | POST/PUT /product/attrgroup，GET /product/attrgroup/{id} |
| AttrGroupRelationDialog | 关联属性管理弹窗（已关联+可关联） | GET /product/attrgroup/{id}/attrs，GET /product/attrgroup/{id}/no-relation/attrs，POST/DELETE /product/attrgroup/relation |
| CategoryTreeSelect | 三级分类树选择器（左侧树+表单内 select） | GET /product/category/tree |

### 4.3 关键交互

- **分类过滤**：左侧分类树点击三级分类 → 右侧表格带 `categoryId` 重新查询；未选中则不带 `categoryId`（查所有）
- **多字段模糊搜索**：`key` 参数同时对组名/描述模糊匹配
- **分组关联属性**：从列表操作列 🔗「关联属性」触发 → 弹出关联弹窗，上区展示已关联属性（可移除），下区点「新增关联」弹出可关联属性多选
- **1:1 校验提示**：批量新增关联时若后端返回 `ATTR_ALREADY_GROUPED`，前端提示"属性 [xxx] 已归属其他分组"
- **删除后列表刷新**：删除成功后停留在当前页，若当前页已无数据则回退到上一页

---

## 五、数据模型

### 5.1 属性分组与关联表

| 表 | 关键字段 | 设计要点 |
|----|---------|---------|
| `pms_attr_group` | `attr_group_name`、`category_id`、`sort` | 分组按分类归属，仅组织基本属性 |
| `pms_attr_attrgroup_relation` | `attr_id`、`attr_group_id`、`attr_sort` | 多对多关联；`uk_attr_group(attr_id, attr_group_id)` 防重复；业务层强制 1:1 |

### 5.2 表结构 `pms_attr_group`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 主键 |
| `attr_group_name` | varchar(64) | 是 | 组名 |
| `sort` | int | 是 | 排序值，默认 0 |
| `descript` | varchar(512) | 否 | 描述 |
| `icon` | varchar(512) | 否 | 组图标 URL |
| `category_id` | bigint | 是 | 所属分类 ID（三级） |
| `create_time` / `update_time` | datetime | 是 | 审计字段 |
| `create_by` / `update_by` | bigint | 否 | 审计字段 |
| `is_deleted` | tinyint | 是 | 逻辑删除 |
| `version` | int | 是 | 乐观锁 |

### 5.3 表结构 `pms_attr_attrgroup_relation`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | bigint | PK | 主键 |
| `attr_id` | bigint | 是 | 属性 ID |
| `attr_group_id` | bigint | 是 | 属性分组 ID |
| `attr_sort` | int | 是 | 组内排序，默认 0 |
| `create_time` / `update_time` | datetime | 是 | 审计字段 |
| `create_by` / `update_by` | bigint | 否 | 审计字段 |
| `is_deleted` | tinyint | 是 | 逻辑删除 |
| `version` | int | 是 | 乐观锁 |

> **不加冗余字段**：关联表不冗余 `attr_name` / `attr_group_name`。关联查询是后台低频操作，join 即可；避免改名同步负担。与品牌关联表（面向前台高频查询）的冗余策略不同。

### 5.4 索引

`pms_attr_group`：

| 索引 | 字段 | 类型 | 说明 |
|------|------|------|------|
| PK | `id` | PRIMARY | 主键 |
| IDX_CATEGORY_ID | `category_id` | NORMAL | 按分类查分组（已有） |
| UK_CATEGORY_GROUP_NAME | `category_id, attr_group_name, is_deleted` | UNIQUE | 同分类分组名唯一（补充） |

`pms_attr_attrgroup_relation`：

| 索引 | 字段 | 类型 | 说明 |
|------|------|------|------|
| PK | `id` | PRIMARY | 主键 |
| UK_ATTR_GROUP | `attr_id, attr_group_id` | UNIQUE | 防重复关联（已有） |
| IDX_ATTR_GROUP_ID | `attr_group_id` | NORMAL | 按分组查属性（已有） |

> 生产环境补充索引 SQL：
> ```sql
> ALTER TABLE pms_attr_group ADD UNIQUE INDEX uk_category_group_name (category_id, attr_group_name, is_deleted);
> ```

---

## 六、接口设计

### 6.1 接口总览

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 分组分页查询 | GET | `/product/attrgroup/list` | `categoryId` 可选、`key` 多字段模糊、分页 |
| 分组详情 | GET | `/product/attrgroup/{id}` | 含 `catelogPath` 数组 |
| 新增分组 | POST | `/product/attrgroup` | |
| 修改分组 | PUT | `/product/attrgroup` | |
| 批量删除分组 | DELETE | `/product/attrgroup/batch` | 校验组下属性 |
| 分组已关联属性 | GET | `/product/attrgroup/{groupId}/attrs` | 联表查询 |
| 分组可关联属性 | GET | `/product/attrgroup/{groupId}/no-relation/attrs` | 未归属任何分组的基本属性 |
| 批量新增关联 | POST | `/product/attrgroup/relation` | 1:1 校验 |
| 批量移除关联 | DELETE | `/product/attrgroup/relation` | 逻辑删除 |

> 所有接口经网关，前端 `baseUrl=/api`，网关将 `/api/product/**` 路由到 `mall-product`（`StripPrefix=1`），与品牌/分类接口共用 `product-route`。

---

### 6.2 属性分组分页查询

**GET** `/product/attrgroup/list?categoryId=225&key=主体&pageNum=1&pageSize=10`

**请求参数**（Query，`AttrGroupQueryDTO`）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `categoryId` | Long | 否 | 三级分类 ID，不传则查所有分类 |
| `key` | String | 否 | 模糊匹配组名 + 描述 |
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
                "id": 1,
                "attrGroupName": "主体",
                "categoryId": 225,
                "categoryName": "手机",
                "sort": 0,
                "icon": "https://oss.example.com/icon/main.png",
                "descript": "手机主体参数"
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
- `LambdaQueryWrapper<AttrGroup>` 拼接条件
- `categoryId` 非空时 `eq`，为空时不加条件（查所有）
- `key` 非空时 `like(attrGroupName, key).or().like(descript, key)`
- 排序：`order by sort asc, id asc`
- 响应中 `categoryName` 需联表或批量查询 `pms_category` 补充

### 6.3 属性分组详情

**GET** `/product/attrgroup/{id}`

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": {
        "id": 1,
        "attrGroupName": "主体",
        "categoryId": 225,
        "categoryName": "手机",
        "catelogPath": [1, 37, 225],
        "sort": 0,
        "icon": "https://oss.example.com/icon/main.png",
        "descript": "手机主体参数"
    }
}
```

**实现要点**：
- 查分组主记录，不存在抛 `ATTR_GROUP_NOT_FOUND`
- `categoryName`：查 `pms_category` 取 name
- `catelogPath`：调用 `ICategoryService.getCatelogPath(categoryId)` 返回 `[一级id, 二级id, 三级id]`

### 6.4 新增属性分组

**POST** `/product/attrgroup`

**请求体**（`AttrGroupSaveDTO`，校验分组 `Create.class`）：

```json
{
    "attrGroupName": "主体",
    "categoryId": 225,
    "sort": 0,
    "icon": "https://oss.example.com/icon/main.png",
    "descript": "手机主体参数"
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `id` | Long | 修改时必填 | `@NotNull(groups = Update.class)` |
| `attrGroupName` | String | 是 | `@NotBlank`，最大 64 字符 |
| `categoryId` | Long | 是 | `@NotNull`，需为三级分类 |
| `sort` | Integer | 否 | `@Min(0)`，默认 0 |
| `icon` | String | 否 | 最大 512 字符 |
| `descript` | String | 否 | 最大 500 字符 |
| `version` | Integer | 修改时必填 | 乐观锁版本号 |

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// 组名重复
{ "code": 54011, "msg": "同分类下分组名已存在", "data": null }

// 分类不存在或非三级
{ "code": 54013, "msg": "分类不存在或非三级分类", "data": null }
```

**业务逻辑**：

```
1. 校验分类存在且 level = 3，否则抛 ATTR_CATELOG_INVALID
2. 校验同分类下分组名唯一（排除逻辑删除），重复抛 ATTR_GROUP_NAME_DUPLICATE
3. 插入 pms_attr_group（审计字段由 MyMetaObjectHandler 填充）
```

### 6.5 修改属性分组

**PUT** `/product/attrgroup`

**请求体**（`AttrGroupSaveDTO`，校验分组 `Update.class`）：

```json
{
    "id": 1,
    "attrGroupName": "主体参数",
    "categoryId": 225,
    "sort": 1,
    "icon": "https://oss.example.com/icon/main.png",
    "descript": "手机主体参数",
    "version": 0
}
```

**业务逻辑**：

```
1. 查询分组，不存在抛 ATTR_GROUP_NOT_FOUND
2. 分组名变更时校验同分类唯一（排除自身 + 逻辑删除）
3. categoryId 变更时校验新分类为三级分类
4. updateById（携带 version 触发乐观锁）
```

### 6.6 批量删除属性分组

**DELETE** `/product/attrgroup/batch`

**请求体**（`AttrGroupBatchDeleteDTO`）：

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

// 组下有属性，拒绝
{ "code": 54012, "msg": "分组 [主体] 下存在属性，无法删除", "data": null }

// id 列表为空
{ "code": 54014, "msg": "批量删除 id 列表不能为空", "data": null }
```

**业务逻辑**：

```
1. ids 为空抛 ATTR_GROUP_BATCH_DELETE_EMPTY
2. 逐个检查引用：SELECT count(*) FROM pms_attr_attrgroup_relation
   WHERE attr_group_id=? AND is_deleted=0
   - 任一 > 0 → 抛 ATTR_GROUP_HAS_ATTRS，携带该分组名（整体事务回滚）
3. 全部通过后统一逻辑删除分组
   （整个流程在同一事务 @Transactional）
```

### 6.7 查询分组已关联属性

**GET** `/product/attrgroup/{groupId}/attrs`

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": [
        {
            "attrId": 10,
            "attrName": "屏幕尺寸",
            "icon": "https://oss.example.com/icon/screen.png",
            "valueSelect": "5.1,5.5,6.1",
            "attrSort": 0
        }
    ]
}
```

**业务逻辑**：

```
1. 校验分组存在，不存在抛 ATTR_GROUP_NOT_FOUND
2. 联表查询：
   SELECT a.id AS attr_id, a.attr_name, a.icon, a.value_select, r.attr_sort
   FROM pms_attr_attrgroup_relation r
   INNER JOIN pms_attr a ON r.attr_id = a.id AND a.is_deleted = 0
   WHERE r.attr_group_id = ? AND r.is_deleted = 0
   ORDER BY r.attr_sort ASC, a.id ASC
3. 返回 AttrRelationVO 列表
```

### 6.8 查询分组可关联属性

**GET** `/product/attrgroup/{groupId}/no-relation/attrs`

> 返回该分组所属分类下，`attr_type ∈ {1,2}` 且在 `pms_attr_attrgroup_relation` 中**无任何关联记录**的基本属性。与 6.7 已关联列表完全互斥。

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": [
        {
            "attrId": 15,
            "attrName": "电池容量",
            "icon": "https://oss.example.com/icon/battery.png",
            "valueSelect": "3000mAh,4000mAh,5000mAh"
        }
    ]
}
```

**业务逻辑**：

```
1. 校验分组存在，获取其 categoryId
2. 查询该分类下未归属任何分组的基本属性：
   SELECT a.id, a.attr_name, a.icon, a.value_select
   FROM pms_attr a
   WHERE a.category_id = ?
     AND a.attr_type IN (1, 2)
     AND a.is_deleted = 0
     AND a.id NOT IN (
         SELECT attr_id FROM pms_attr_attrgroup_relation
         WHERE is_deleted = 0
     )
   ORDER BY a.sort ASC, a.id ASC
3. 返回 AttrSimpleVO 列表
```

### 6.9 批量新增分组-属性关联

**POST** `/product/attrgroup/relation`

**请求体**（`AttrRelationSaveDTO`）：

```json
{
    "groupId": 1,
    "attrIds": [10, 15, 20]
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `groupId` | Long | 是 | `@NotNull`，分组需存在 |
| `attrIds` | `List<Long>` | 是 | `@NotEmpty`，每个元素 `@NotNull` |

**响应**：

```json
// 成功
{ "code": 200, "msg": "success", "data": {} }

// 属性已归属其他分组
{ "code": 54015, "msg": "属性 [屏幕尺寸] 已归属其他分组", "data": null }
```

**业务逻辑**：

```
1. 校验分组存在
2. 逐个校验 attrIds：
   a. 校验属性存在（is_deleted = 0）
   b. 校验属性 categoryId 与分组 categoryId 一致（跨分类不允许关联）
   c. 1:1 校验：SELECT count(*) FROM pms_attr_attrgroup_relation
      WHERE attr_id=? AND is_deleted=0 AND attr_group_id != ?
      - > 0 → 抛 ATTR_ALREADY_GROUPED，携带该属性名（整体事务回滚）
3. 批量插入关联记录（attr_sort 默认 0，审计字段自动填充）
   （整个流程在同一事务 @Transactional）
```

### 6.10 批量移除分组-属性关联

**DELETE** `/product/attrgroup/relation`

**请求体**（`AttrRelationRemoveDTO`）：

```json
{
    "groupId": 1,
    "attrIds": [10, 15]
}
```

**响应**：

```json
{ "code": 200, "msg": "success", "data": {} }
```

**业务逻辑**：

```
1. 逻辑删除：
   UPDATE pms_attr_attrgroup_relation SET is_deleted=1
   WHERE attr_group_id=? AND attr_id IN (?) AND is_deleted=0
```

---

## 七、DTO / VO 定义

```
com.mymall.product.dto.attrgroup/
├── AttrGroupSaveDTO.java              // 属性分组新增/修改（校验分组 Create/Update）
├── AttrGroupQueryDTO.java             // 属性分组分页查询（继承 PageQuery）
├── AttrGroupBatchDeleteDTO.java       // 批量删除属性分组
├── AttrRelationSaveDTO.java           // 批量新增分组-属性关联
└── AttrRelationRemoveDTO.java         // 批量移除分组-属性关联

com.mymall.product.vo.attrgroup/
├── AttrGroupVO.java                   // 属性分组详情（含 categoryName/catelogPath）
├── AttrGroupListVO.java               // 分组列表项（含 categoryName，不含 path）
├── AttrRelationVO.java                // 分组已关联属性列表项
└── AttrSimpleVO.java                  // 分组可关联属性列表项
```

### 7.1 AttrGroupSaveDTO

```java
@Data
@Schema(description = "新增/修改属性分组")
public class AttrGroupSaveDTO {

    @NotNull(groups = Update.class, message = "分组ID不能为空")
    @Schema(description = "分组ID（修改时必填）")
    private Long id;

    @NotBlank(message = "组名不能为空")
    @Size(max = 64, message = "组名最长 64 字符")
    @Schema(description = "组名", example = "主体")
    private String attrGroupName;

    @NotNull(message = "所属分类ID不能为空")
    @Schema(description = "所属三级分类ID", example = "225")
    private Long categoryId;

    @Min(value = 0, message = "排序值不能小于 0")
    @Schema(description = "排序值")
    private Integer sort;

    @Size(max = 512, message = "图标地址最长 512 字符")
    @Schema(description = "组图标 URL")
    private String icon;

    @Size(max = 500, message = "描述最长 500 字符")
    @Schema(description = "描述")
    private String descript;

    @NotNull(groups = Update.class, message = "版本号不能为空")
    @Schema(description = "乐观锁版本号（修改时必填）")
    private Integer version;
}
```

### 7.2 AttrGroupQueryDTO

```java
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "属性分组分页查询条件")
public class AttrGroupQueryDTO extends PageQuery {

    @Schema(description = "三级分类ID（不传则查所有分类）")
    private Long categoryId;

    @Schema(description = "模糊匹配组名 + 描述")
    private String key;
}
```

### 7.3 AttrGroupBatchDeleteDTO

```java
@Data
@Schema(description = "批量删除属性分组")
public class AttrGroupBatchDeleteDTO {

    @NotEmpty(message = "id列表不能为空")
    @Schema(description = "分组ID列表")
    private List<Long> ids;
}
```

### 7.4 AttrRelationSaveDTO

```java
@Data
@Schema(description = "批量新增分组-属性关联")
public class AttrRelationSaveDTO {

    @NotNull(message = "分组ID不能为空")
    @Schema(description = "属性分组ID")
    private Long groupId;

    @NotEmpty(message = "属性ID列表不能为空")
    @Schema(description = "属性ID列表")
    private List<Long> attrIds;
}
```

### 7.5 AttrRelationRemoveDTO

```java
@Data
@Schema(description = "批量移除分组-属性关联")
public class AttrRelationRemoveDTO {

    @NotNull(message = "分组ID不能为空")
    @Schema(description = "属性分组ID")
    private Long groupId;

    @NotEmpty(message = "属性ID列表不能为空")
    @Schema(description = "属性ID列表")
    private List<Long> attrIds;
}
```

### 7.6 AttrGroupVO

```java
@Data
@Schema(description = "属性分组详情")
public class AttrGroupVO {
    private Long id;
    private String attrGroupName;
    private Long categoryId;
    private String categoryName;
    /** 分类完整路径：[一级id, 二级id, 三级id] */
    private List<Long> catelogPath;
    private Integer sort;
    private String icon;
    private String descript;
}
```

### 7.7 AttrGroupListVO

```java
@Data
@Schema(description = "属性分组列表项")
public class AttrGroupListVO {
    private Long id;
    private String attrGroupName;
    private Long categoryId;
    private String categoryName;
    private Integer sort;
    private String icon;
    private String descript;
}
```

### 7.8 AttrRelationVO

```java
@Data
@Schema(description = "分组已关联属性列表项")
public class AttrRelationVO {
    private Long attrId;
    private String attrName;
    private String icon;
    private String valueSelect;
    private Integer attrSort;
}
```

### 7.9 AttrSimpleVO

```java
@Data
@Schema(description = "分组可关联属性列表项")
public class AttrSimpleVO {
    private Long attrId;
    private String attrName;
    private String icon;
    private String valueSelect;
}
```

---

## 八、Service 接口设计

### 8.1 IAttrGroupService

```java
public interface IAttrGroupService extends IService<AttrGroup> {

    /** 分组分页查询（categoryId 可选） */
    PageVO<AttrGroupListVO> pageQuery(AttrGroupQueryDTO query);

    /** 分组详情（含 categoryName + catelogPath） */
    AttrGroupVO getGroupDetail(Long id);

    /** 新增分组 */
    void saveGroup(AttrGroupSaveDTO dto);

    /** 修改分组 */
    void updateGroup(AttrGroupSaveDTO dto);

    /** 批量删除分组（校验组下属性） */
    void batchDelete(AttrGroupBatchDeleteDTO dto);
}
```

### 8.2 IAttrAttrgroupRelationService

```java
public interface IAttrAttrgroupRelationService extends IService<AttrAttrgroupRelation> {

    /** 查询分组已关联的属性列表 */
    List<AttrRelationVO> listAttrsByGroup(Long groupId);

    /** 查询分组可关联的属性列表（该分类下未归属任何分组的基本属性） */
    List<AttrSimpleVO> listNoRelationAttrs(Long groupId);

    /** 批量新增关联（1:1 校验） */
    void saveRelations(AttrRelationSaveDTO dto);

    /** 批量移除关联 */
    void removeRelations(AttrRelationRemoveDTO dto);

    /**
     * 属性的分组关系 upsert（供 AttrService 新增/修改时调用）
     * <p>
     * - attrGroupId 为 null：解除该属性的所有关联
     * - attrGroupId 非空：校验分类一致 + 1:1 校验，然后删旧建新
     *
     * @param attrId      属性ID
     * @param attrGroupId 目标分组ID（null 表示解除关联）
     */
    void upsertRelation(Long attrId, Long attrGroupId);
}
```

> `upsertRelation` 供 [属性管理](./attr-management.md) 的新增/修改属性接口调用，实现分组关系的自动维护。

---

## 九、错误码

在 `ResultCode` 中补充属性分组相关错误码（**54010~54015 码段**）：

| 错误码 | 枚举值 | 消息 | 触发场景 |
|--------|--------|------|---------|
| 54010 | `ATTR_GROUP_NOT_FOUND` | 属性分组不存在 | ID 查询不到 |
| 54011 | `ATTR_GROUP_NAME_DUPLICATE` | 同分类下分组名已存在 | 新增/改名 |
| 54012 | `ATTR_GROUP_HAS_ATTRS` | 分组下存在属性，无法删除 | 删除分组时有关联属性 |
| 54013 | `ATTR_CATELOG_INVALID` | 分类不存在或非三级分类 | categoryId 校验失败（属性分组与属性共用） |
| 54014 | `ATTR_GROUP_BATCH_DELETE_EMPTY` | 批量删除 id 列表不能为空 | 分组 `ids` 为空 |
| 54015 | `ATTR_ALREADY_GROUPED` | 属性已归属其他分组 | 1:1 校验失败 |

对应枚举（追加到 `ResultCode`）：

```java
// ==================== 属性分组 54010+ ====================
ATTR_GROUP_NOT_FOUND(54010, "属性分组不存在"),
ATTR_GROUP_NAME_DUPLICATE(54011, "同分类下分组名已存在"),
ATTR_GROUP_HAS_ATTRS(54012, "分组下存在属性，无法删除"),
ATTR_CATELOG_INVALID(54013, "分类不存在或非三级分类"),
ATTR_GROUP_BATCH_DELETE_EMPTY(54014, "批量删除 id 列表不能为空"),
ATTR_ALREADY_GROUPED(54015, "属性已归属其他分组"),
```

> `ATTR_CATELOG_INVALID`（54013）为属性分组与属性共用错误码，属性管理模块也引用此码。
> 同时在 `ResultCode` 头部码段规划注释中补：`54001~54019 - 商品属性/属性分组`。

---

## 十、分类管理补充接口

属性分组详情需要返回分类完整路径 `catelogPath`，需在分类管理模块补充。

### 10.1 Service 层

`ICategoryService` 增加方法：

```java
/**
 * 获取分类的完整路径（从一级到当前层级）
 *
 * @param categoryId 分类ID
 * @return 路径列表，如 [一级id, 二级id, 三级id]
 */
List<Long> getCatelogPath(Long categoryId);
```

**实现逻辑**：

```
1. 查询当前分类，获取 parentId
2. 向上回溯：parentId != 0 时继续查父分类，直到 parentId = 0
3. 反转路径列表，返回 [一级id, ..., 当前id]
```

### 10.2 HTTP 接口

**GET** `/product/category/{categoryId}/path`

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": [1, 37, 225]
}
```

> 供前端级联选择器回显用。该接口同时补充到 [category-management.md](./category-management.md) 的接口总览。

### 10.3 CategoryServiceImpl 改名同步冗余

`CategoryServiceImpl.updateCategory()` 改名时，除了已有的刷新 `pms_category_brand_relation.category_name`（见品牌管理实现清单第 12 项），还需刷新：
- `pms_attr.category_id` 不含名称，无需刷新
- `pms_attr_group.category_id` 不含名称，无需刷新

> 属性分组、属性表只存 `category_id` 不冗余分类名，分类改名无需同步。仅品牌关联表冗余了 `category_name`。

---

## 十一、网关路由

与分类/品牌接口共用 `product-route` 路由，详见 [品牌管理 - 网关路由](./brand-management.md#八网关路由)。

---

## 十二、HTTP 调试文件

`http/product-attrgroup-demo.http`

```http
### ==================== 属性分组 ====================

### 1. 分组分页查询（按分类）
GET http://localhost:1000/api/product/attrgroup/list?categoryId=225&pageNum=1&pageSize=10

### 2. 分组分页查询（查所有 + 关键词）
GET http://localhost:1000/api/product/attrgroup/list?key=主体&pageNum=1&pageSize=10

### 3. 分组详情
GET http://localhost:1000/api/product/attrgroup/1

### 4. 新增分组
POST http://localhost:1000/api/product/attrgroup
Content-Type: application/json

{
    "attrGroupName": "主体",
    "categoryId": 225,
    "sort": 0,
    "icon": "https://oss.example.com/icon/main.png",
    "descript": "手机主体参数"
}

### 5. 修改分组
PUT http://localhost:1000/api/product/attrgroup
Content-Type: application/json

{
    "id": 1,
    "attrGroupName": "主体参数",
    "categoryId": 225,
    "sort": 1,
    "version": 0
}

### 6. 批量删除分组
DELETE http://localhost:1000/api/product/attrgroup/batch
Content-Type: application/json

{
    "ids": [1, 2]
}

### 7. 查询分组已关联属性
GET http://localhost:1000/api/product/attrgroup/1/attrs

### 8. 查询分组可关联属性
GET http://localhost:1000/api/product/attrgroup/1/no-relation/attrs

### 9. 批量新增关联
POST http://localhost:1000/api/product/attrgroup/relation
Content-Type: application/json

{
    "groupId": 1,
    "attrIds": [10, 15]
}

### 10. 批量移除关联
DELETE http://localhost:1000/api/product/attrgroup/relation
Content-Type: application/json

{
    "groupId": 1,
    "attrIds": [10]
}

### ==================== 分类路径（补充） ====================

### 11. 获取分类完整路径
GET http://localhost:1000/api/product/category/225/path
```

---

## 十三、非功能性要求

> 模块级非功能性要求（性能、缓存、事务、检索同步等）见 [商品中心概述](./overview.md)。

| 项目 | 要求 |
|------|------|
| 性能 | 分页查询 < 200ms（命中 `idx_category_id`）；详情聚合查询 < 300ms |
| 并发 | 分组为低频写操作，乐观锁（`@Version`）即可 |
| 缓存 | 属性分组按分类缓存到 Redis（TTL 30 分钟），写操作后主动失效 |
| 事务 | 批量新增关联跨多条 `pms_attr_attrgroup_relation`，必须 `@Transactional` |
| 安全 | 管理接口需管理员权限（网关 JWT 鉴权，待实现） |
| 日志 | 写操作记录操作人 + 变更内容；查询不记录 |
| 幂等 | 新增靠同分类名唯一约束兜底；关联新增靠 `uk_attr_group` 兜底 |

---

## 十四、实现清单

| 序号 | 任务 | 文件 |
|------|------|------|
| 1 | 补充 ResultCode 属性分组错误码（54010~54015）+ 码段注释 | `mall-common/.../exception/ResultCode.java` |
| 2 | 创建属性分组 DTO/VO（5 个 DTO + 4 个 VO） | `mall-product/.../dto/attrgroup/` + `.../vo/attrgroup/` |
| 3 | 扩展 IAttrGroupService + 实现 AttrGroupServiceImpl | `mall-product/.../service/` |
| 4 | 扩展 IAttrAttrgroupRelationService + 实现 AttrAttrgroupRelationServiceImpl（含 upsertRelation） | `mall-product/.../service/` |
| 5 | 创建 AttrGroupController（分组 CRUD + 关联管理 9 个接口） | `mall-product/.../controller/AttrGroupController.java` |
| 6 | ICategoryService 补充 getCatelogPath 方法 + HTTP 接口 | `mall-product/.../service/` + `CategoryController.java` |
| 7 | 创建 HTTP 调试文件 | `http/product-attrgroup-demo.http` |
| 8 | 补充索引 SQL（uk_category_group_name） | `init/mysql/mymall_pms.sql` |
| 9 | 同步更新 overview.md（1:1 业务约束）+ category-management.md 接口总览 | `docs/mall-product/` |
