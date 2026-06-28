# 功能页面实现

> 前置：已学完 01–09 篇。本篇对照项目代码，讲解两个真实业务页面（分类管理、品牌管理）的实现思路和关键逻辑，重点不是逐行翻译，而是说清「为什么这么写」。

## 1. 两个页面要解决什么问题

后台管理 90% 的工作是「列表 + 弹窗」式 CRUD，但真实业务里总会冒出几个不那么标准的诉求。本篇选的两个页面，恰好覆盖了两类典型场景：

| 页面 | 数据形态 | 典型难点 |
|------|----------|----------|
| 分类管理 | 树形（三级分类） | 拖拽排序、层级约束、循环引用检测 |
| 两个页面共用的模式：弹窗管理、刷新策略、错误处理 | | |
| 品牌管理 | 列表（分页） | 状态切换回滚、当前页删空回退、关联分类弹窗 |

学完本篇你应该能回答：

- 分类树的拖拽排序，前端怎么算出「受影响节点」、怎么防止拖成 4 级、怎么防止拖到自己子节点下？
- 品牌的「显示状态」开关失败后为什么能自动回弹，不需要手动恢复 UI？
- 列表页删到最后一条，怎么自动跳回上一页，而不是停在一个空页？
- `defineExpose` + `ref` 这套弹窗调用模式相比 `v-model` 传参有什么好处？

---

## 2. 分类管理页面

涉及文件：

- `src/views/product/category/index.vue` —— 页面主体（工具栏 + 树 + 弹窗编排）
- `src/views/product/category/components/CategoryNode.vue` —— 自定义树节点
- `src/views/product/category/components/CategoryForm.vue` —— 新增/编辑弹窗

### 2.1 页面整体结构

`index.vue` 的模板就三大块，对应页面上看到的三块区域：

```vue
<div class="category-page app-container">
  <!-- ① 工具栏：拖拽开关 / 展开 / 折叠 / 新增 / 批量删除 -->
  <div class="toolbar card-box">...</div>

  <!-- ② 树形区：el-tree + 自定义节点 + 空状态 -->
  <div class="tree-container card-box">
    <el-tree ...>
      <template #default="{ data }">
        <CategoryNode :data="data" @add="..." @edit="..." @delete="..." />
      </template>
    </el-tree>
  </div>

  <!-- ③ 弹窗：新增/编辑（通过 ref 调用 openCreate/openEdit 打开） -->
  <CategoryForm ref="formRef" v-model="formVisible" @success="handleFormSuccess" />
</div>
```

这种「页面 = 工具栏 + 数据区 + 弹窗」的结构是后台页面的标准骨架。页面本身只负责编排（数据加载、事件分发、弹窗调度），具体渲染逻辑下沉到子组件。

### 2.2 树数据加载与渲染

数据加载很直接，`onMounted` 调一次 `loadTree()`：

```typescript
async function loadTree() {
  loading.value = true
  try {
    treeData.value = await getCategoryTree()
    // 默认展开一级分类，避免进来看到一堆折叠节点
    defaultExpandedKeys.value = treeData.value.map((item) => item.catId)
  } catch {
    // 错误已由拦截器处理（见 09 篇）
  } finally {
    loading.value = false
  }
}
```

`getCategoryTree()` 后端直接返回完整的嵌套树（`CategoryVO.children`），前端不做二次组装。`defaultExpandedKeys` 取所有一级分类的 `catId`，配合 `el-tree` 的 `:default-expanded-keys` 实现「进来默认展开一级」。

> **为什么用 `default-expanded-keys` 而不是 `default-expand-all`？** 三级分类全展开节点太多，体验差；只展开一级，用户按需往下点。而且 `default-expand-all` 在数据动态加载时行为不稳定。

`el-tree` 的关键配置：

```vue
<el-tree
  ref="treeRef"
  :data="treeData"
  :props="{ label: 'name', children: 'children' }"
  node-key="catId"
  :default-expanded-keys="defaultExpandedKeys"
  show-checkbox
  :draggable="dragEnabled"
  :allow-drop="allowDrop"
  @node-drop="handleDrop"
  @check="handleCheckChange"
>
```

- `node-key="catId"`：指定节点唯一标识，拖拽、勾选、`getNode()` 都靠它
- `:props`：把后端字段 `name` / `children` 映射成 tree 期望的 `label` / `children`
- `show-checkbox`：开启勾选框，批量删除用
- `:draggable` 绑定一个开关 `dragEnabled`，默认关闭——拖拽是危险操作，不能默认开着

### 2.3 CategoryNode：自定义节点行

`el-tree` 默认只渲染一个文本标签，但分类节点要显示「名称 + 层级标签 + 商品数 + 操作按钮」，所以用 `#default` 插槽塞一个 `CategoryNode` 组件。

**层级标签着色**：用 `el-tag` 的 `type` 区分层级，一眼能看出节点深度：

```typescript
const levelTagType = (level: number) => {
  if (level === 1) return undefined  // 默认蓝色
  if (level === 2) return 'success'  // 绿色
  return 'info'                      // 灰色（三级）
}
```

**操作按钮布局**：用 flex 把按钮推到行末：

```scss
.category-node {
  display: flex;
  align-items: center;
  flex: 1;
  .node-actions {
    margin-left: auto;   // 关键：把操作按钮推到最右
    white-space: nowrap;
  }
}
```

**一级分类不显示删除**：删除一级分类会连带删掉整棵子树，风险大，UI 上直接隐藏入口：

```vue
<el-button v-if="data.catLevel > 1" link type="danger" @click.stop="emit('delete', data)">
  删除
</el-button>
```

**`@click.stop` 的作用**：按钮点击事件不冒泡到 `el-tree` 的节点内容区，避免点「新增」时误触发节点选中/展开。所有操作按钮都加了 `.stop`。

### 2.4 CategoryForm：新增/编辑弹窗

这个弹窗要同时承担「新增一级」「新增子分类」「编辑」三种场景，靠 `mode` + `lockedParent` 两个状态区分。

**核心是 `defineExpose` 暴露的两个方法**：

```typescript
async function openCreate(parentNode?: CategoryVO) {
  mode.value = 'create'
  lockedParent.value = !!parentNode          // 传了父节点 → 锁定父级选择器
  formData.value = {
    name: '',
    parentCid: parentNode ? parentNode.catId : '0',  // 不传 → 顶级（parentCid='0'）
    sort: 0, icon: '', productUnit: ''
  }
  await loadTreeData()    // 加载分类树供父级选择
  visible.value = true
}

async function openEdit(node: CategoryVO) {
  mode.value = 'update'
  lockedParent.value = true   // 编辑时父级永远只读（改层级走拖拽，不走表单）
  formData.value = { catId: node.catId, name: node.name, parentCid: node.parentCid, ... }
  await loadTreeData()
  visible.value = true
}

defineExpose({ openCreate, openEdit })
```

父组件这样调用：

```typescript
function handleAddRoot()  { formRef.value?.openCreate() }        // 新增一级
function handleAddChild(data: CategoryVO) { formRef.value?.openCreate(data) }  // 新增子分类
function handleEdit(data: CategoryVO)    { formRef.value?.openEdit(data) }    // 编辑
```

> **为什么用 `defineExpose` + `ref` 调用，而不是父组件维护 `mode` 传给子组件？** 因为「打开弹窗 + 初始化数据 + 加载下拉数据」是一个原子动作，封装在子组件内部，父组件只喊一声「打开新增」就行，不用关心弹窗内部细节。这样新增/编辑逻辑内聚在 `CategoryForm` 里，父组件保持精简。这种模式在 4.2 节展开讲。

**父级分类锁定**：`el-tree-select` 的 `:disabled` 同时考虑两种情况：

```vue
<el-tree-select
  v-model="formData.parentCid"
  :disabled="lockedParent || mode === 'update'"
  ...
/>
```

- 新增子分类时 `lockedParent=true`（不能改父级，否则就跑偏了）
- 编辑时 `mode==='update'`（层级调整走拖拽，不在表单改）
- 只有「新增一级分类」时 `lockedParent=false` 且 `mode==='create'`，可以选择任意父级

**提交时区分模式**：编辑模式刻意不带 `parentCid`，因为后端 `CategoryUpdateDTO` 也不含该字段——层级调整只能走拖拽接口：

```typescript
if (mode.value === 'create') {
  const { name, parentCid, sort, icon, productUnit } = formData.value
  await createCategory({ name, parentCid, sort, icon, productUnit })
} else {
  const { catId, name, sort, icon, productUnit } = formData.value  // 不取 parentCid
  await updateCategory({ catId: catId!, name, sort, icon, productUnit })
}
```

**错误处理细节**：`handleSubmit` 的 try/catch 里，失败时不手动关弹窗：

```typescript
try {
  await formRef.value.validate()
  submitting.value = true
  // ... 调接口
  visible.value = false   // 只有成功才关
  emit('success')
} catch (err) {
  // 校验失败 / 提交失败：弹窗保持打开，用户可修改后重试
} finally {
  submitting.value = false
}
```

这是项目的统一约定——失败时弹窗不关，让用户看到当前输入、决定是改了重试还是手动取消。错误提示由 axios 拦截器统一弹 `ElMessage`，业务代码不用重复弹。

### 2.5 拖拽排序（重点）

拖拽是分类管理最复杂的部分，分三步：**开关 → 校验 → 落地**。

#### 2.5.1 拖拽开关

```vue
<span class="drag-switch-label">开启拖拽</span>
<el-switch v-model="dragEnabled" />
```

`el-tree` 的 `:draggable="dragEnabled"` 绑定这个开关。默认关闭，用户明确开启后才能拖。这是对危险操作的保护——分类层级一乱，商品归类全乱。

#### 2.5.2 allowDrop：拖拽前校验

`el-tree` 的 `:allow-drop` 在拖拽过程中实时调用，返回 `false` 就禁止放下。这个函数要拦住三类非法拖拽：

```typescript
function allowDrop(draggingNode, dropNode, type: 'before' | 'after' | 'inner') {
  const draggingData = draggingNode.data as CategoryVO
  const dropData = dropNode.data as CategoryVO

  // ① 计算拖拽后的目标层级
  let targetLevel: number
  if (type === 'inner') {
    targetLevel = dropData.catLevel + 1   // 拖进 dropNode 内部 → 子级
  } else {
    targetLevel = dropData.catLevel       // 拖到 dropNode 前/后 → 同级
  }

  // ② 层级不能超过 3
  if (targetLevel > 3) return false

  // ③ 拖拽节点自身层级没超，但它带着一坨子孙，子孙深度 + 目标层级也不能超 3
  let maxChildLevel = 0
  if (draggingData.children) {
    traverseTree(draggingData.children, (node) => {
      const relativeLevel = node.catLevel - draggingData.catLevel  // 相对拖拽节点的深度差
      if (relativeLevel > maxChildLevel) maxChildLevel = relativeLevel
    })
  }
  if (targetLevel + maxChildLevel > 3) return false

  // ④ 不能拖到自己子节点下（循环引用）
  if (type === 'inner') {
    if (isDescendant(treeData.value, draggingData.catId, dropData.catId, 'catId')) {
      return false
    }
  }

  return true
}
```

**`type` 三种值的含义**：

| type | 含义 | 目标层级 |
|------|------|----------|
| `inner` | 拖成 dropNode 的子节点 | `dropData.catLevel + 1` |
| `before` | 放到 dropNode 前面（同级） | `dropData.catLevel` |
| `after` | 放到 dropNode 后面（同级） | `dropData.catLevel` |

**第 ③ 点最容易漏**：假设拖拽节点是个二级分类，它下面还挂着三级子孙。如果把它拖到另一个一级分类内部（变成二级），逻辑上没问题（`targetLevel=2`）。但如果拖拽节点本身是二级、下面挂着三级，你把它拖到一个三级节点**内部**（`targetLevel=4`），子孙就会变成 5 级——所以必须算子孙的最大相对深度一起判断。`traverseTree` 是项目 `utils/tree.ts` 提供的深度优先遍历工具。

**第 ④ 点循环引用**：把节点 A 拖到它自己的子孙 B 内部，会形成 A→B→A 的环。`isDescendant(tree, ancestorId, targetId, idKey)` 判断 `targetId` 是不是 `ancestorId` 的子孙——这里判断 `dropData.catId` 是否是 `draggingData.catId` 的子孙，是就禁止。只有 `type==='inner'` 才需要查（before/after 是同级，不会产生父子环）。

#### 2.5.3 handleDrop：收集变更 + 落地

`el-tree` 的 `@node-drop` 在拖拽**完成后**触发。此时 DOM 已经变了，但后端数据还没动。这里的任务是把「DOM 上的新状态」翻译成「后端要更新的字段列表」。

```typescript
async function handleDrop(draggingNode, dropNode, dropType) {
  const draggingData = draggingNode.data as CategoryVO
  const dropData = dropNode.data as CategoryVO

  // ① 算新父节点和新层级
  let newParentCid: string
  let newLevel: number
  if (dropType === 'inner') {
    newParentCid = dropData.catId
    newLevel = dropData.catLevel + 1
  } else {
    newParentCid = dropData.parentCid   // 同级 → 父级也是 dropNode 的父级
    newLevel = dropData.catLevel
  }

  // ② 从 el-tree 的 store 拿到「新父节点」下的所有子节点（已按 DOM 顺序排好）
  const store = treeRef.value?.store
  const newParentNode = newParentCid === '0' ? store.root : store.getNode(newParentCid)
  const sortItems: CategorySortItem[] = newParentNode.childNodes.map(
    (child: any, index: number) => ({
      catId: child.data.catId,
      parentCid: newParentCid,
      catLevel: newLevel,
      sort: index                     // 按当前 DOM 顺序重新编号 0,1,2...
    })
  )
```

**关键点：sort 怎么算？** 拖完后，新父节点下的子节点在 DOM 里已经有顺序了（`el-tree` 自动排好），`newParentNode.childNodes` 就是这个顺序。直接遍历取下标当 `sort` 值即可——后端按 `sort` 字段持久化，刷新后顺序一致。

**跨层级拖拽要更新子孙的 catLevel**：

```typescript
  // ③ 如果拖拽节点层级变了，它带着的子孙 catLevel 也要跟着变
  if (draggingData.catLevel !== newLevel) {
    const updateChildrenLevel = (nodes: CategoryVO[], oldParentLevel: number) => {
      nodes.forEach((child) => {
        const newChildLevel = oldParentLevel + 1
        sortItems.push({
          catId: child.catId,
          parentCid: child.parentCid,   // 子孙的父级不变
          catLevel: newChildLevel,      // 只改层级
          sort: child.sort
        })
        if (child.children) {
          updateChildrenLevel(child.children, newChildLevel)
        }
      })
    }
    updateChildrenLevel(draggingData.children, newLevel)
  }
```

举个例子：把一个二级分类（`catLevel=2`）拖到另一个一级分类内部，它变成二级（`newLevel=2`，没变）——这种情况子孙不用动。但如果把它拖到顶级（`newLevel=1`），它变成一级，原来挂在它下面的三级子孙要变成二级，递归往下。`updateChildrenLevel` 就是干这个的。

**确认框 + 调接口 + 失败回弹**：

```typescript
  try {
    await ElMessageBox.confirm(
      `将「${draggingData.name}」从「${fromParentName}」移动到「${toParentName}」下，确认操作？`,
      '拖拽确认',
      { type: 'info', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
    await sortCategories(sortItems)
    ElMessage.success('排序成功')
    loadTree()   // 成功 → 重新拉树，确保前后端一致
  } catch (err) {
    loadTree()   // 失败/取消 → 重新拉树，把 DOM 回滚到和服务端一致
  }
}
```

注意 `catch` 里无论用户取消还是接口失败，都调 `loadTree()`。因为 `@node-drop` 触发时 DOM 已经被 `el-tree` 改了，但后端没改——必须重新拉数据把 DOM 拉回原状。这是「DOM 已变但数据未变」场景的标准回滚手段。

### 2.6 批量删除与展开/折叠

**批量删除**：靠 `el-tree` 的 `show-checkbox` + `getCheckedNodes()`：

```typescript
function handleCheckChange() {
  if (treeRef.value) {
    checkedNodes.value = treeRef.value.getCheckedNodes() as CategoryVO[]
  }
}

async function handleBatchDelete() {
  const names = checkedNodes.value.map((n) => n.name).join('、')
  await ElMessageBox.confirm(`已选中 ${checkedNodes.value.length} 个分类：${names}，确定删除吗？`, ...)
  const ids = checkedNodes.value.map((n) => n.catId)
  await batchDeleteCategories(ids)
  ElMessage.success('删除成功')
  checkedNodes.value = []   // 清空选中
  loadTree()
}
```

`batchDeleteDisabled` 用 `computed` 联动按钮禁用状态，没勾选时按钮灰掉：

```typescript
const batchDeleteDisabled = computed(() => checkedNodes.value.length === 0)
```

**展开/折叠全部**：直接操作 `el-tree` 内部的 Node 对象：

```typescript
function expandAll() {
  const store = treeRef.value?.store
  if (!store) return
  const traverse = (node: any) => {
    node.expand(null, true)   // expand(递归, 静默)
    if (node.childNodes) node.childNodes.forEach((child: any) => traverse(child))
  }
  store.root.childNodes.forEach((child: any) => traverse(child))
}
```

`collapseAll` 同理，调 `node.collapse()`。这里用到了 `el-tree` 内部 store 的 API（不是公开文档推荐的用法，但项目里这样写最直接），所以 `node` 参数用了 `any`——这是有意为之的类型放宽，因为 el-tree 内部 Node 结构没有公开类型。

---

## 3. 品牌管理页面

涉及文件：

- `src/views/product/brand/index.vue` —— 页面主体（PageTable + 两个弹窗）
- `src/views/product/brand/components/BrandForm.vue` —— 新增/编辑弹窗
- `src/views/product/brand/components/BrandRelationDialog.vue` —— 关联分类弹窗

品牌是典型的「列表 + 分页 + 弹窗」CRUD，但有几个细节值得讲：状态切换回滚、删空回退、关联分类弹窗。

### 3.1 PageTable 组件使用

品牌页几乎没有自己写表格，全交给 `PageTable` 组件（08 篇讲过设计）。页面只提供「配置」和「插槽」：

```vue
<PageTable
  ref="tableRef"
  :columns="columns"
  :fetch="getBrandPage"
  :search-fields="searchFields"
  selectable
  @selection-change="handleSelectionChange"
>
  <template #toolbar>...</template>
  <template #col-logo="{ row }">...</template>
  <template #col-showStatus="{ row }">...</template>
  <template #col-actions="{ row }">...</template>
</PageTable>
```

**searchFields 配置**：声明式描述搜索栏长什么样，`PageTable` 自动渲染：

```typescript
const searchFields = [
  { prop: 'name', label: '品牌名', component: 'input', placeholder: '...', width: '200px' },
  { prop: 'firstLetter', label: '首字母', component: 'select', options: letterOptions, ... },
  { prop: 'showStatus', label: '显示状态', component: 'select', options: statusOptions, ... }
]
```

`PageTable` 内部把 `searchParams` 和分页参数合并后传给 `fetch`：

```typescript
// PageTable 内部
const res = await props.fetch({
  pageNum: pageNum.value,
  pageSize: pageSize.value,
  ...searchParams.value
})
```

**columns 配置**：`slot: true` 的列，`PageTable` 会把内容交给具名插槽 `col-{prop}` 渲染：

```typescript
const columns = [
  { prop: 'logo', label: 'Logo', width: 120, slot: true },         // 用 col-logo 插槽
  { prop: 'name', label: '品牌名称', width: 180 },                  // 默认渲染文本
  { prop: 'showStatus', label: '显示状态', width: 120, slot: true },// 用 col-showStatus 插槽
  { prop: 'actions', label: '操作', width: 240, fixed: 'right', slot: true }
]
```

`PageTable` 内部的实现：

```vue
<el-table-column v-for="col in columns" :key="col.prop" ...>
  <template v-if="col.slot" #default="scope">
    <slot :name="`col-${col.prop}`" :row="scope.row" :index="scope.$index" />
  </template>
</el-table-column>
```

这种「配置 + 具名插槽」模式让列表页代码量极少——品牌页整个 `<template>` 不到 70 行，却完成了搜索栏、工具栏、自定义列、分页、勾选。

### 3.2 显示状态切换：失败自动回滚

品牌有「显示/隐藏」状态，列表里用 `el-switch` 直接切换：

```vue
<template #col-showStatus="{ row }">
  <el-switch
    :model-value="row.showStatus === 1"
    @change="(val: boolean) => handleStatusChange(row, val)"
  />
</template>
```

注意这里**没有用 `v-model`**，而是 `:model-value` + `@change`。这是回滚的关键：

```typescript
async function handleStatusChange(row: BrandVO, val: boolean) {
  const newStatus = val ? 1 : 0
  try {
    await updateBrandShowStatus(row.id, newStatus)
    row.showStatus = newStatus          // 成功才改 row
    ElMessage.success('状态更新成功')
  } catch {
    // 失败不修改 row.showStatus
    // 因为 el-switch 绑定的是 :model-value（row.showStatus===1）
    // row 没变 → switch 自动回弹到原状态
  }
}
```

**对比两种绑定方式**：

| 写法 | 失败后表现 |
|------|------------|
| `v-model="row.showStatus"` | switch 立即改 row，失败后需手动把 row 改回来 |
| `:model-value` + `@change` | row 不动，失败时 switch 自动回弹（推荐） |

`v-model` 会双向绑定，用户拨动 switch 的瞬间就改了 `row.showStatus`，失败后得手动恢复。而 `:model-value` 是单向的，switch 显示什么完全由 `row.showStatus` 决定——只要成功才改 `row`，失败时 `row` 没动，switch 自然回到原位。这是个很实用的小技巧。

### 3.3 删除逻辑：当前页删空自动回退

单个删除：

```typescript
async function handleDelete(row: BrandVO) {
  await ElMessageBox.confirm(`确定删除品牌「${row.name}」吗？`, ...)
  await deleteBrand(row.id)
  ElMessage.success('删除成功')

  // 若当前页只剩这一条且不是第一页，回退到上一页
  const table = tableRef.value
  if (table && table.tableData.value.length === 1 && table.pageNum.value > 1) {
    table.pageNum.value--
  }
  tableRef.value?.refresh()
}
```

**为什么要回退？** 假设当前在第 5 页，每页 10 条，这页只剩 1 条。删掉后直接 `refresh()` 会请求第 5 页，但第 5 页已经没数据了——后端返回空列表，表格显示空白，分页器还停在「第 5 页」，体验很差。所以删之前判断「这页是不是只剩这一条」，是就把 `pageNum` 减 1，再 refresh 请求第 4 页。

`tableData` 和 `pageNum` 是 `PageTable` 通过 `defineExpose` 暴露出来的：

```typescript
// PageTable 内部
defineExpose({ refresh, loadData, tableData, pageNum, pageSize, total })
```

批量删除的回退逻辑类似，多判断一个「当前页数据是否全部被选中删掉」：

```typescript
if (table.tableData.value.length === selectedIds.value.length && table.pageNum.value > 1) {
  table.pageNum.value--
}
```

### 3.4 BrandForm：编辑先拉详情拿 version

`BrandForm` 和 `CategoryForm` 结构类似，重点讲两个差异。

**编辑模式先拉详情**：列表传进来的 `row` 不一定带 `version`（乐观锁版本号），所以编辑时要重新请求详情接口：

```typescript
async function openEdit(row: BrandVO) {
  mode.value = 'update'
  visible.value = true   // 先开弹窗（给加载态留位置）
  try {
    const detail = await getBrandDetail(row.id)   // 拿到含 version 的完整数据
    formData.value = {
      id: detail.id,
      name: detail.name,
      logo: detail.logo,
      // ...
      version: detail.version    // 关键：乐观锁版本号
    }
  } catch {
    visible.value = false   // 拉详情失败 → 关弹窗
  }
}
```

提交时带上 `version`：

```typescript
await updateBrand(formData.value)   // 携带 version 触发后端乐观锁
```

后端 MyBatis-Plus 的 `@Version` 注解会拿这个 `version` 做并发控制——如果有人在你编辑期间改过这条记录，`version` 对不上，更新会失败。这是防止「A 改了 logo、B 同时改了名称」互相覆盖的标准手段。

> **为什么 `openEdit` 先开弹窗再拉详情，而不是拉完再开？** 为了让用户点击「编辑」后立刻看到弹窗（哪怕是个加载中状态），体验更跟手。如果先 `await` 拉详情再开弹窗，网络慢时用户点了没反应，会以为卡死。

**Logo URL 输入 + 预览**：

```vue
<el-form-item label="品牌 Logo" prop="logo">
  <div class="logo-uploader">
    <div v-if="formData.logo" class="logo-preview">
      <el-image :src="formData.logo" fit="contain" class="logo-img" />
      <el-button link type="danger" @click="formData.logo = ''">移除</el-button>
    </div>
    <el-input v-model="formData.logo" placeholder="请输入 Logo 图片 URL">
      <template #prepend>URL</template>
    </el-input>
    <div class="logo-tip">本期通过 URL 输入 Logo 地址，后续支持 OSS 直传</div>
  </div>
</el-form-item>
```

本期用 URL 输入框 + `el-image` 预览的简化方案，留了注释说明后续接 OSS 直传（对应项目 `docs/mall-product/object-storage-design.md` 的设计）。这是个典型的「先跑通流程、再迭代上传方式」的做法——表单结构不变，只把输入框换成上传组件即可。

### 3.5 BrandRelationDialog：关联分类弹窗（重点）

品牌和分类是多对多关系（一个品牌可关联多个分类，一个分类下可有多个品牌）。这个弹窗管理「某个品牌关联了哪些三级分类」。

#### 整体结构

```vue
<el-dialog v-model="visible" title="关联分类" ...>
  <!-- 头部：品牌名 + 新增关联按钮 -->
  <div class="relation-header">
    <span>品牌：{{ brandName }}</span>
    <el-button v-if="!showAddForm" @click="handleShowAdd">新增关联</el-button>
  </div>

  <!-- 新增表单（点击后才显示） -->
  <div v-if="showAddForm" class="add-form">
    <el-tree-select v-model="selectedCatelogId" :data="categoryTree" ... />
    <el-button @click="handleAddRelation">确认关联</el-button>
  </div>

  <!-- 已关联列表 -->
  <el-table :data="relations" ...>
    <el-table-column prop="brandName" label="品牌名" />
    <el-table-column prop="catelogName" label="分类名" />
    <el-table-column label="操作">移除</el-table-column>
  </el-table>
</el-dialog>
```

弹窗内部分两块：上方是「新增关联」的折叠表单（`showAddForm` 控制），下方是「已关联列表」。点「新增关联」才展开选择器，避免一直占空间。

#### 查询已关联列表

弹窗打开时（`watch(visible)`）加载：

```typescript
watch(visible, (val) => {
  if (val && props.brandId) {
    loadRelations()      // 打开 → 加载关联列表
  } else {
    showAddForm.value = false   // 关闭 → 重置新增表单状态
  }
})

async function loadRelations() {
  loading.value = true
  try {
    relations.value = await getBrandRelations(props.brandId)
  } catch {
    // 拦截器处理
  } finally {
    loading.value = false
  }
}
```

每次打开弹窗都重新拉一次，保证数据最新。关闭时把 `showAddForm` 重置为 `false`，下次打开默认收起新增表单。

#### 新增关联：只能选三级分类

业务约束：品牌只能关联到**叶子分类（三级）**，因为一级/二级分类太宽泛，关联了没意义。用 `el-tree-select` 的 `disabled` 函数实现：

```vue
<el-tree-select
  v-model="selectedCatelogId"
  :data="categoryTree"
  :props="{
    label: 'name',
    value: 'catId',
    children: 'children',
    disabled: (data: CategoryVO) => data.catLevel !== 3   // 非三级 → 灰掉
  }"
  check-strictly
  node-key="catId"
  placeholder="请选择三级分类"
/>
```

`disabled` 是个函数，每个节点都会调一次，返回 `true` 就禁用该节点。这里 `data.catLevel !== 3` 把一、二级分类全灰掉，用户只能点三级叶子。

> **`check-strictly` 的作用**：`el-tree-select` 默认父子联动勾选，但这里父节点已经 disabled 了，且业务上只选单个三级节点，配 `check-strictly` 让选择行为独立，不级联。

#### 分类树的复用

分类树数据来自分类管理的同一个接口：

```typescript
import { getCategoryTree } from '@/api/product/category'

async function loadCategoryTree() {
  categoryTree.value = await getCategoryTree()
}
```

这里复用了 `getCategoryTree()`，没有为品牌页单独造接口。这是后端 API 设计合理带来的好处——分类树是公共数据，谁需要谁调。

**懒加载**：分类树只在用户第一次点「新增关联」时加载，之后复用：

```typescript
async function handleShowAdd() {
  showAddForm.value = true
  selectedCatelogId.value = undefined
  if (categoryTree.value.length === 0) {   // 只在没加载过时拉
    await loadCategoryTree()
  }
}
```

如果用户只查看/移除关联、不新增，就不会请求分类树，省一次接口调用。

#### 移除关联

```typescript
async function handleRemoveRelation(row: BrandRelationVO) {
  await ElMessageBox.confirm(
    `确定移除品牌「${row.brandName}」与分类「${row.catelogName}」的关联吗？`,
    '移除确认',
    { type: 'warning', ... }
  )
  await deleteBrandRelation(props.brandId, row.catelogId)
  ElMessage.success('移除成功')
  loadRelations()   // 刷新列表
}
```

移除只删关联关系，不删品牌也不删分类——这是多对多关系表的标准操作。确认框文案带上品牌名和分类名，让用户明确知道在移除哪条关系。

---

## 4. 通用模式总结

两个页面虽然业务不同，但都遵循几套通用模式。理解这些模式，写其他 CRUD 页面能直接套。

### 4.1 CRUD 列表页标准模式：PageTable + FormDialog 组合

品牌页是标准范例：

```
PageTable（负责列表 + 搜索 + 分页 + 勾选）
   ├── :columns / :search-fields / :fetch   ← 声明式配置
   ├── #toolbar 插槽                          ← 新增/批量删除按钮
   └── #col-{prop} 插槽                       ← 自定义列（状态开关、操作按钮）

FormDialog（负责新增/编辑）
   ├── defineExpose({ openCreate, openEdit }) ← 父组件 ref 调用
   ├── v-model="visible"                      ← 父组件控制显隐
   └── @success                               ← 成功后通知父组件刷新
```

分类页因为数据是树形，没用 `PageTable`，但弹窗管理模式完全一样。这套组合让「新增一个 CRUD 页面」的工作量集中在两处：写 `columns`/`searchFields` 配置、写 `FormDialog`。表格/分页/搜索栏的样板代码被 `PageTable` 吃掉了。

### 4.2 弹窗管理模式：defineExpose + ref 调用

项目的弹窗都遵循这套模式，子组件暴露 `openXxx`，父组件通过 `ref` 调用：

```typescript
// 子组件（FormDialog）
function openCreate(data?: any) { ... ; visible.value = true }
function openEdit(row: any)    { ... ; visible.value = true }
defineExpose({ openCreate, openEdit })

// 父组件
const formRef = ref<InstanceType<typeof FormDialog>>()
function handleAdd()  { formRef.value?.openCreate() }
function handleEdit(row) { formRef.value?.openEdit(row) }
```

**对比「父组件传 mode + data 给子组件」的写法**：

| | defineExpose + ref | 父组件传 props |
|------|------|------|
| 打开弹窗 | 一句 `formRef.value?.openEdit(row)` | 设 `visible=true` + `mode='edit'` + `editData=row` |
| 初始化数据 | 在 `openEdit` 内部（可 async 拉详情） | 子组件 `watch(visible)` 里判断 mode 再拉 |
| 父组件复杂度 | 低（只喊一声） | 高（要维护 mode/editData） |
| 内聚性 | 高（打开逻辑全在子组件） | 低（逻辑分散在父子两边） |

尤其 `BrandForm.openEdit` 要先 `await getBrandDetail` 拉详情，这种「打开时带异步初始化」的场景，`defineExpose` 模式比 `watch(visible)` 干净得多。

### 4.3 刷新策略

两类页面的刷新方式不同，对应两种数据源：

| 页面 | 数据源 | 刷新方式 |
|------|--------|----------|
| 分类（树形） | 页面自己维护 `treeData` | 调 `loadTree()` 重新拉整棵树 |
| 品牌（分页） | `PageTable` 内部维护 `tableData` | 调 `tableRef.value.refresh()` |

品牌页的 `handleFormSuccess` 就一行：

```typescript
function handleFormSuccess() {
  tableRef.value?.refresh()
}
```

`refresh()` 是 `PageTable` 通过 `defineExpose` 暴露的方法，内部调 `loadData()`（用当前 `pageNum`/`pageSize`/`searchParams` 重新请求）。父组件不用关心分页状态，交给 `PageTable` 即可。

分类页的弹窗成功回调：

```typescript
function handleFormSuccess() {
  loadTree()
}
```

树形数据没有「分页」概念，每次全量重拉最简单，也保证了拖拽/删除后前后端一致。

### 4.4 错误处理：拦截器统一 + catch 中不关弹窗

项目的错误处理分两层：

**第一层：axios 拦截器统一弹 `ElMessage`**（09 篇讲过）。业务代码调接口时，失败会自动弹错误提示，不用每个 `catch` 里手动 `ElMessage.error`。

**第二层：业务代码的 `try/catch` 负责控制流程**，主要是「失败时保持弹窗打开」：

```typescript
async function handleSubmit() {
  try {
    await formRef.value.validate()   // 校验失败抛错 → 进 catch
    submitting.value = true
    await createCategory(...)        // 接口失败抛错 → 进 catch
    visible.value = false            // 只有走到这（成功）才关弹窗
    emit('success')
  } catch (err) {
    // 弹窗不关，用户可修改后重试
  } finally {
    submitting.value = false
  }
}
```

注释里反复出现的「错误已由拦截器处理」就是指第一层。业务 `catch` 不重复弹提示，只管流程控制。这套约定让错误处理代码很简洁——`catch` 块常常是空的或只一句注释，但语义明确：「失败了，啥也别做，让弹窗留着」。

**回滚场景的特殊处理**：

- 状态切换失败 → 不改 `row`，`el-switch` 自动回弹（3.2 节）
- 拖拽失败/取消 → `loadTree()` 把 DOM 拉回原状（2.5.3 节）
- 删除后当前页空 → `pageNum--` 后再 refresh（3.3 节）

这些都是「DOM 已变但数据未变」或「数据变了但 UI 要补偿」的场景，各自有对应手段，但共同原则是：**失败后让 UI 和后端数据保持一致**。

---

## 5. 小结

| 模式 | 关键点 | 出现位置 |
|------|--------|----------|
| 树形页结构 | 工具栏 + el-tree + 弹窗，页面只编排 | 分类页 `index.vue` |
| 自定义树节点 | `#default` 插槽 + flex 布局 + `@click.stop` | `CategoryNode.vue` |
| 弹窗模式 | `defineExpose({ openCreate, openEdit })` + 父组件 ref 调用 | `CategoryForm` / `BrandForm` |
| 拖拽校验 | `allowDrop` 拦层级 + 拦循环引用 | 分类页 `allowDrop` |
| 拖拽落地 | 从 `el-tree` store 读 DOM 顺序算 sort，跨层级更新子孙 catLevel | 分类页 `handleDrop` |
| 失败回弹 | `:model-value` + `@change` 不改 row → switch 自动回弹 | 品牌页状态切换 |
| 删空回退 | 删前判断当前页是否将空，`pageNum--` 后 refresh | 品牌页删除 |
| 乐观锁 | 编辑先拉详情拿 `version`，提交带上 | `BrandForm.openEdit` |
| 关联弹窗 | `el-tree-select` + `disabled` 函数限制只选三级 | `BrandRelationDialog` |
| 刷新策略 | 树形用 `loadTree()`，分页用 `tableRef.refresh()` | 两页通用 |
| 错误处理 | 拦截器弹提示 + 业务 catch 控流程（失败不关弹窗） | 全项目约定 |

这两个页面基本覆盖了后台管理系统里最常见的交互模式：树形 CRUD + 拖拽、列表 CRUD + 状态切换、多对多关联管理。把本篇的拖拽校验、状态回滚、弹窗模式、刷新策略几套套路记住，再写其他业务页面（属性管理、规格管理、订单管理等）就是套模板 + 改配置的事。
