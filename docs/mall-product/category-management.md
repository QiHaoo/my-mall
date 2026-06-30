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
| 品牌 | `pms_category_brand_relation` | `category_id` |
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
- 按 `sort` 升序排列（sort 相同时按 `id` 升序）
- 前端树形控件需要 `children` 字段嵌套子分类
- 三级分类最大深度为 3，超过不允许

#### F2 批量删除分类

- **引用检查**：被删除的分类（含子分类）下不能有关联数据
  - 不能有关联商品（`pms_spu_info.category_id`）
  - 不能有关联品牌（`pms_category_brand_relation.category_id`）
  - 不能有关联优惠券分类（`sms_coupon_spu_category_relation.category_id`）
- **级联子分类**：删除父分类时，其所有子孙分类必须一并检查引用
- **逻辑删除**：不物理删除记录，将 `show_status` 置为 `0`（隐藏）
- **根分类保护**：`parent_id = 0` 的一级分类不允许删除（防止误操作清空整个分类树）

#### F3 新增分类

- 分类名称在同级下唯一（同一 `parent_id` 下 `name` 不能重复）
- `level` 由父分类 `level + 1` 自动计算，前端不传
- 层级限制：`level` 最大为 3（不允许创建四级分类）
- `sort` 默认值 0，前端可指定

#### F4 修改分类

- **基础信息修改**：名称、图标、排序、计量单位等
- **拖拽排序**：前端拖拽节点到新位置，传入新的 `parent_id`、`level`、`sort`
- **批量拖拽**：多个节点同时拖拽，传入排序列表
- 拖拽后需重新计算受影响分类的 `level`（子分类的层级跟随父分类变化）
- 拖拽限制：不能将自己拖拽为自己的子节点（循环引用）

---

## 三、前端设计

### 页面布局

> Figma 设计稿：[商品中心 / 分类管理](https://www.figma.com/design/UPX2R9RSakc3klzVIEB6Hq/My-mall?node-id=12-397)（含列表页 + 表单弹窗两个 Frame）
>
> 以下文字描述与 Figma 设计稿互为补充，Figma 展示视觉布局，文字描述交互逻辑。

**分类管理页**（管理后台 `/product/category`）

采用左侧树 + 工具栏的单页布局（无分页，全量树加载）：

```
┌─ 工具栏 ────────────────────────────────────────────────────┐
│  [开启拖拽 Switch]  [展开全部] [折叠全部]  [新增一级分类]  [批量删除] │
├─ 树形区域 ──────────────────────────────────────────────────┤
│  □ ▶ 手机、数码、通讯                            [+] [✎]     │
│  □ ▶ 图书、音像、电子书刊                        [+] [✎]     │
│  □ ▼ 大家电                                      [+] [✎]     │
│  □   ├ 空调                            [+] [✎] [🗑]   │
│  □   └ 冰箱                            [+] [✎] [🗑]   │
│  □ ▶ 家用电器                                    [+] [✎]     │
│  □ ▶ 家居家装                                    [+] [✎]     │
│  ...                                                        │
└────────────────────────────────────────────────────────────┘
```

**工具栏**：
- `开启拖拽`：el-switch，关闭时树节点不可拖拽（默认关闭，防止误操作）；开启后可拖拽节点调整层级和排序
- `展开全部` / `折叠全部`：一键展开/折叠整棵树
- `新增一级分类`：el-button primary，打开新增弹窗
- `批量删除`：el-button danger，勾选节点后启用（至少勾选一个），点击弹出确认框

**树形区域**（el-tree + custom node）：
- 每一行节点包含：复选框 + 展开/折叠箭头 + 分类名称 + 层级标签（el-tag，一级：蓝色、二级：绿色、三级：灰色）
- 行内操作按钮（hover 时显示）：
  - `[+]` 新增子分类：打开新增弹窗，父级自动锁定为当前节点
  - `[✎]` 编辑：打开编辑弹窗，回填当前节点数据
  - `[🗑]` 删除：仅二级、三级节点显示；一级分类不显示删除按钮（防止误删根节点）
- 树节点支持 checkbox 勾选（show-checkbox），用于批量删除
- 拖拽开启时支持节点拖拽（draggable），支持跨层级拖放（如二级拖到一级下变二级，或拖到三级下非法→自动回弹）

**分类表单弹窗**（新增 / 编辑复用，宽度 520px）：

| 字段 | 组件 | 说明 |
|------|------|------|
| 分类名称 | el-input | 必填，最大 50 字符 |
| 上级分类 | el-tree-select | 新增子分类时锁定为当前节点；新增一级分类时显示"顶级分类"；编辑时 disabled（只读），层级调整通过拖拽实现 |
| 排序值 | el-input-number | 默认 0，最小值 0 |
| 图标 | el-input（URL 输入） | 图标地址，最大 255 字符；非必填，可结合 OSS 直传填充 |
| 计量单位 | el-input | 最大 50 字符，非必填 |

> 表单字段与 `CategorySaveDTO` / `CategoryUpdateDTO` 保持对齐：新增/编辑均包含 name、parentId（编辑只读）、sort、icon、productUnit。编辑时不传 parentId（后端 DTO 无此字段），层级调整统一走拖拽排序接口。

**拖拽确认**：拖拽结束后不立即提交，弹出轻量提示框显示变更预览（如"将'空调'从'大家电'移动到'家用电器'下"），用户确认后调用排序接口；取消则回弹到原位置。

### 组件拆分

| 组件 | 职责 | 消费接口 |
|------|------|---------|
| CategoryTree | 分类管理页主体：工具栏 + 树 + 弹窗 | GET /product/category/tree |
| CategoryForm | 新增/编辑分类表单弹窗 | POST/PUT /product/category，GET /product/category/tree（父级选择） |
| CategoryNode | 树节点行渲染：名称/标签/操作按钮 | 无（纯展示组件） |
| DragConfirmPopover | 拖拽变更确认浮层 | 无（触发 PUT /product/category/sort） |

### 关键交互

- **加载分类树**：进入页面调用 `GET /product/category/tree`，全量加载（数据量 < 1000 条），用 `v-loading` 遮罩；加载失败弹 `el-notification` 提示
- **新增一级分类**：点击工具栏按钮 → 打开弹窗 → 上级分类默认"顶级分类"（parentId=0）→ 提交调用 `POST /product/category` → 成功后刷新树并展开到新节点位置
- **新增子分类**：点击行内 `[+]` → 打开弹窗 → 上级分类锁定为当前节点（不可改）→ 提交 → 成功后展开父节点、刷新树
- **编辑分类**：点击行内 `[✎]` → 打开弹窗回填数据（上级分类 disabled 只读）→ 提交调用 `PUT /product/category` → 成功后刷新树（保证图标、计量单位等字段与服务端一致）
- **删除单个分类**：二级/三级节点点击 `[🗑]` → 弹确认框（"确定删除分类 [xxx] 及其子分类？"）→ 确认后加入批量删除队列调用 `POST /product/category/batch-delete`
- **批量删除**：勾选多个节点 → 点击批量删除 → 弹确认框显示选中数量和名称列表 → 确认调用 `POST /product/category/batch-delete` → 成功后刷新树；后端返回引用错误（如 `51002 CATEGORY_HAS_PRODUCTS`）时前端展示具体被引用的分类名
- **拖拽排序**：开启拖拽开关 → 拖放节点到目标位置 → 弹确认浮层显示变更预览 → 确认后前端计算所有受影响节点的 `parentId` / `level` / `sort` → 调用 `PUT /product/category/sort` → 成功后刷新树；失败（如循环引用 `51006`、超过三级 `51004`）弹错误提示并回弹
- **展开/折叠**：单个节点点击箭头切换；工具栏按钮遍历树节点设置 `expanded` 状态
- **一级分类保护**：前端层面不显示一级分类的删除按钮；即使通过接口直接调用，后端也会返回 `51007 CATEGORY_ROOT_DELETE` 拒绝删除
- **表单校验**：前端校验名称必填、名称同级唯一（提交时由后端最终校验，返回 `51005` 时定位到名称字段提示）

### 与截图参考的改进点

相比谷粒商城原始界面，本设计做了以下生产级改进：

| 维度 | 原版（截图） | 本项目改进 |
|------|------------|-----------|
| 拖拽安全 | 默认开启拖拽，易误操作 | 默认关闭拖拽，switch 开关控制，拖拽后需二次确认 |
| 批量操作 | 无复选框视觉反馈，批量删除按钮常驻 | checkbox 勾选、批量删除按钮 disabled 直到有选中项 |
| 删除保护 | 所有节点都有 Delete 按钮 | 一级分类不显示删除按钮，后端二次保护 |
| 表单体验 | 行内 Append/edit 文字链接 | 按钮（+ / ✎ / 🗑）hover 显示，新增弹窗代替行内编辑 |
| 层级标识 | 无视觉区分 | 一级/二级/三级用不同颜色 tag 标识 |
| 展开折叠 | 只能逐个点击 | 工具栏提供展开全部/折叠全部 |

---

## 四、数据模型

### 4.1 表结构 `pms_category`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | bigint AUTO_INCREMENT | PK | 分类 ID |
| `name` | char(50) | 是 | 分类名称 |
| `parent_id` | bigint | 是 | 父分类 ID，一级分类为 0 |
| `level` | int | 是 | 层级（1/2/3） |
| `show_status` | tinyint | 是 | 显示状态：0-隐藏，1-显示 |
| `sort` | int | 是 | 排序值，越小越靠前 |
| `icon` | char(255) | 否 | 图标地址 |
| `product_unit` | char(50) | 否 | 计量单位 |
| `product_count` | int | 否 | 商品数量（冗余，定时刷新） |

### 4.2 索引

| 索引 | 字段 | 类型 | 说明 |
|------|------|------|------|
| PK | `id` | PRIMARY | 主键 |
| IDX_PARENT | `parent_id, show_status, sort` | NORMAL | 子分类查询（覆盖排序） |
| UK_PARENT_NAME | `parent_id, name` | UNIQUE | 同级名称唯一 |

> 生产环境需执行索引创建 SQL：
> ```sql
> ALTER TABLE pms_category ADD INDEX idx_parent (parent_id, show_status, sort);
> ALTER TABLE pms_category ADD UNIQUE INDEX uk_parent_name (parent_id, name);
> ```

---

## 五、接口设计

### 5.1 接口总览

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 分类树 | GET | `/product/category/tree` | 返回完整三级树 |
| 批量删除 | POST | `/product/category/batch-delete` | 逻辑删除（含引用检查） |
| 新增分类 | POST | `/product/category` | 创建分类节点 |
| 修改分类 | PUT | `/product/category` | 修改基础信息 |
| 拖拽排序 | PUT | `/product/category/sort` | 拖拽调整层级/排序 |
| 分类完整路径 | GET | `/product/category/{categoryId}/path` | 返回 `[一级id,二级id,三级id]`，供级联选择器回显（属性管理模块需要，见 [attr-management.md 第十章](./attr-management.md#十分类管理补充接口)） |

> 所有接口经过网关（`localhost:1000`），前端 `baseUrl` 为 `/api`，网关将 `/api/product/**` 路由到 `mall-product` 服务（StripPrefix=1 去掉 `/api` 前缀）。

### 5.2 分类树

**GET** `/product/category/tree`

**请求参数**：无

**响应**：

```json
{
    "code": 200,
    "msg": "success",
    "data": [
        {
            "id": 1,
            "name": "图书、音像、电子书刊",
            "parentId": 0,
            "level": 1,
            "showStatus": 1,
            "sort": 0,
            "icon": "https://xxx.com/icon1.png",
            "productUnit": "件",
            "productCount": 128,
            "children": [
                {
                    "id": 2,
                    "name": "电子书刊",
                    "parentId": 1,
                    "level": 2,
                    "showStatus": 1,
                    "sort": 0,
                    "icon": null,
                    "productUnit": null,
                    "productCount": 36,
                    "children": [
                        {
                            "id": 3,
                            "name": "电子书",
                            "parentId": 2,
                            "level": 3,
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
- 按 `sort` 升序 → `id` 升序排列

### 5.3 批量删除

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
2. 检查是否为一级分类（parent_id = 0），是则拒绝
3. 递归查询每个分类的所有子孙分类 ID
4. 合并所有待删除分类 ID（含子孙）
5. 检查引用：
   a. pms_spu_info 中是否有 category_id 在待删除列表中
   b. pms_category_brand_relation 中是否有 category_id 在待删除列表中
   c. sms_coupon_spu_category_relation 中是否有 category_id 在待删除列表中
6. 任一引用存在 → 抛 BizException，返回具体被引用的分类名
7. 无引用 → UPDATE pms_category SET show_status = 0 WHERE id IN (...)
```

### 5.4 新增分类

**POST** `/product/category`

**请求体**（`CategorySaveDTO`）：

```json
{
    "name": "手机通讯",
    "parentId": 1,
    "sort": 5,
    "icon": "https://xxx.com/icon.png",
    "productUnit": "台"
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `name` | String | 是 | `@NotBlank`，最大 50 字符 |
| `parentId` | Long | 是 | `@NotNull`，一级分类传 0 |
| `sort` | Integer | 否 | 默认 0，`@Min(0)` |
| `icon` | String | 否 | URL 格式，最大 255 字符 |
| `productUnit` | String | 否 | 最大 50 字符 |

> `level` 由后端根据 `parentId` 自动计算（父分类 `level + 1`），前端不传。

**响应**：

```json
{ "code": 200, "msg": "success", "data": {} }
```

**业务逻辑**：

```
1. 如果 parentId != 0：
   a. 查询父分类，不存在则抛异常
   b. 计算 level = 父分类.level + 1
   c. 如果 level > 3，抛异常"分类最多支持三级"
2. 如果 parentId == 0，level = 1
3. 检查同级名称唯一（同 parentId 下 name 不重复）
4. 设置 showStatus = 1，productCount = 0
5. 插入记录
```

### 5.5 修改分类

**PUT** `/product/category`

**请求体**（`CategoryUpdateDTO`）：

```json
{
    "id": 5,
    "name": "智能手机",
    "sort": 3,
    "icon": "https://xxx.com/icon2.png",
    "productUnit": "台"
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `id` | Long | 是 | `@NotNull` |
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

### 5.6 拖拽排序

**PUT** `/product/category/sort`

**请求体**（`CategorySortDTO`）：

```json
{
    "categories": [
        {
            "id": 5,
            "parentId": 2,
            "level": 2,
            "sort": 1
        },
        {
            "id": 6,
            "parentId": 2,
            "level": 2,
            "sort": 2
        },
        {
            "id": 7,
            "parentId": 1,
            "level": 2,
            "sort": 1
        }
    ]
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|---------|
| `categories` | `List<SortItem>` | 是 | `@NotEmpty`，最多 100 |
| `categories[].id` | Long | 是 | `@NotNull` |
| `categories[].parentId` | Long | 是 | `@NotNull` |
| `categories[].level` | Integer | 是 | `@NotNull`，范围 1~3 |
| `categories[].sort` | Integer | 是 | `@NotNull`，`@Min(0)` |

**响应**：

```json
{ "code": 200, "msg": "success", "data": {} }
```

**业务逻辑**：

```
1. 遍历 categories 列表：
   a. 检查 id 对应的分类是否存在
   b. 如果 parentId 有变化，检查不能形成循环引用
      （自己不能成为自己的祖先）
   c. 如果 level 有变化，递归更新所有子孙分类的 level
2. 批量更新 parent_id、level、sort 字段
```

---

## 六、DTO 定义

```
com.mymall.product.dto.category/
├── CategorySaveDTO.java        // 新增
├── CategoryUpdateDTO.java      // 修改
├── CategorySortDTO.java        // 拖拽排序
├── CategoryBatchDeleteDTO.java // 批量删除
└── CategoryVO.java             // 树形查询返回（含 children）
```

### 6.1 CategoryVO

```java
@Data
@Schema(description = "分类树节点")
public class CategoryVO {
    private Long id;
    private String name;
    private Long parentId;
    private Integer level;
    private Integer showStatus;
    private Integer sort;
    private String icon;
    private String productUnit;
    private Integer productCount;

    @Schema(description = "子分类列表")
    private List<CategoryVO> children;
}
```

### 6.2 CategorySaveDTO

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
    private Long parentId;

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

### 6.3 CategoryUpdateDTO

```java
@Data
@Schema(description = "修改分类")
public class CategoryUpdateDTO {
    @NotNull(message = "分类ID不能为空")
    @Schema(description = "分类ID", example = "5")
    private Long id;

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

### 6.4 CategorySortDTO

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
        private Long id;

        @NotNull
        private Long parentId;

        @NotNull @Min(1) @Max(3)
        private Integer level;

        @NotNull @Min(0)
        private Integer sort;
    }
}
```

### 6.5 CategoryBatchDeleteDTO

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

## 七、错误码

在 `ResultCode` 枚举中补充商品分类相关错误码（50001+ 码段）：

| 错误码 | 枚举值 | 消息 | 触发场景 |
|--------|--------|------|---------|
| 50001 | `CATEGORY_NOT_FOUND` | 分类不存在 | ID 查询不到 |
| 50002 | `CATEGORY_HAS_CHILDREN` | 分类下存在子分类，无法删除 | 删除时有子分类被引用 |
| 50003 | `CATEGORY_HAS_PRODUCTS` | 分类下存在关联商品，无法删除 | 删除时 `pms_spu_info` 有引用 |
| 50004 | `CATEGORY_HAS_BRANDS` | 分类下存在关联品牌，无法删除 | 删除时品牌关联表有引用 |
| 50005 | `CATEGORY_LEVEL_EXCEEDED` | 分类最多支持三级 | 新增/拖拽时 level > 3 |
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

## 八、网关路由

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

## 九、HTTP 调试文件

`http/product-category-demo.http`

```http
### 1. 分类树查询
GET http://localhost:1000/api/product/category/tree

### 2. 新增一级分类
POST http://localhost:1000/api/product/category
Content-Type: application/json

{
    "name": "测试一级分类",
    "parentId": 0,
    "sort": 100,
    "icon": "https://example.com/icon.png",
    "productUnit": "件"
}

### 3. 新增二级分类
POST http://localhost:1000/api/product/category
Content-Type: application/json

{
    "name": "测试二级分类",
    "parentId": {{parentId}},
    "sort": 1,
    "productUnit": "个"
}

### 4. 修改分类名称
PUT http://localhost:1000/api/product/category
Content-Type: application/json

{
    "id": {{id}},
    "name": "修改后的名称",
    "sort": 2
}

### 5. 拖拽排序（将 id=5 从一级移动到二级）
PUT http://localhost:1000/api/product/category/sort
Content-Type: application/json

{
    "categories": [
        { "id": 5, "parentId": 2, "level": 2, "sort": 1 },
        { "id": 6, "parentId": 2, "level": 2, "sort": 2 }
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

## 十、非功能性要求

| 项目 | 要求 |
|------|------|
| 性能 | 分类树查询 < 100ms（数据量 < 1000，内存组装） |
| 并发 | 分类为低频写操作，不需要分布式锁 |
| 缓存 | 分类树可缓存到 Redis（TTL 10 分钟），写操作后主动失效缓存 |
| 安全 | 分类管理接口需要管理员权限（网关 JWT 鉴权，待实现） |
| 日志 | 写操作记录操作人 + 变更内容（审计需要） |
| 幂等 | 拖拽排序天然幂等（覆盖写），删除用逻辑删除不冲突 |

---

## 十一、实现清单

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
