# 通用组件与 Composables 设计

> 本文档记录前端项目自建的复用资产（通用组件、组合式函数、工具函数）的设计意图与使用约定。
> 视觉与交互规范见 [design-system.md](design-system.md)，不在此重复。

---

## 一、组件速查表

| 资产 | 类型 | 文件 | 职责 | 状态 |
|------|------|------|------|------|
| PageTable | 通用组件 | `components/PageTable/index.vue` | 搜索栏+工具栏+表格+分页一体化 CRUD 列表 | ✅ |
| FormDialog | 通用组件 | `components/FormDialog/index.vue` | 弹窗+表单+校验+提交 loading | ✅ |
| useTable | Composable | `composables/useTable.ts` | 分页表格状态管理（独立使用） | ✅ |
| useDialog | Composable | `composables/useDialog.ts` | 弹窗可见性与模式管理 | ✅ |
| request | 工具 | `utils/request.ts` | Axios 实例+拦截器+泛型方法 | ✅ |
| tree | 工具 | `utils/tree.ts` | 树形数据遍历/查找/路径/子孙检测 | ✅ |
| ImageUpload | 通用组件 | - | OSS Presigned URL 直传 | ❌ 计划中 |
| TreeSelect | 通用组件 | - | 树形选择器封装 | ❌ 计划中 |
| RichTextEditor | 通用组件 | - | 富文本编辑器 | ❌ 计划中 |
| SvgIcon | 通用组件 | - | 自定义 SVG 图标 | ❌ 计划中 |

---

## 二、PageTable 通用分页表格

封装「搜索栏 + 工具栏插槽 + el-table + el-pagination」标准 CRUD 列表模式，内部自管理数据加载与分页状态。

### 2.1 设计思路

PageTable **内部自管理**表格数据、loading、分页状态，父组件通过 `ref` 调用暴露的方法控制刷新，无需自行维护分页逻辑。

> **与 useTable 的关系**：PageTable 和 useTable 都封装了分页逻辑，存在功能重叠。实际开发中发现同时使用两者会导致状态同步问题（品牌管理页面曾因此出现新增后列表不刷新的 bug，详见 [PROGRESS.md](PROGRESS.md)）。
>
> **推荐用法**：
> - 标准分页列表 → 用 PageTable（内部已封装分页逻辑，通过 ref 刷新）
> - 非标准列表（如树形全量加载、自定义分页交互）→ 用 useTable 独立管理

### 2.2 Props

| Prop | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `columns` | `Column[]` | 必填 | 列配置 |
| `fetch` | `(params) => Promise<PageVO<any>>` | 必填 | 分页查询函数，接收 `{ pageNum, pageSize, ...search }` |
| `searchFields` | `SearchField[]` | `[]` | 搜索栏字段配置 |
| `rowKey` | `string` | `'id'` | 行数据主键 |
| `selectable` | `boolean` | `false` | 是否显示复选框列 |
| `defaultPageSize` | `number` | `10` | 默认每页条数 |

**Column 配置**：

```typescript
interface Column {
  prop: string
  label: string
  width?: number | string
  align?: 'left' | 'center' | 'right'
  fixed?: boolean | 'left' | 'right'
  slot?: boolean  // 是否使用插槽自定义列内容，插槽名为 col-{prop}
}
```

**SearchField 配置**：

```typescript
interface SearchField {
  prop: string
  label: string
  component: 'input' | 'select'
  placeholder?: string
  options?: { label: string; value: string | number }[]
  width?: string
}
```

### 2.3 Slots

| Slot | 说明 | 作用域参数 |
|------|------|-----------|
| `toolbar` | 工具栏区域（新增按钮、批量操作） | - |
| `col-{prop}` | 列自定义内容（`col.slot: true` 时启用） | `{ row, index }` |

### 2.4 暴露方法（defineExpose）

| 方法/属性 | 类型 | 说明 |
|-----------|------|------|
| `refresh()` | `() => void` | 刷新当前页数据 |
| `loadData()` | `() => void` | 加载数据（与 refresh 等效） |
| `tableData` | `Ref<any[]>` | 当前表格数据 |
| `pageNum` | `Ref<number>` | 当前页码 |
| `pageSize` | `Ref<number>` | 每页条数 |
| `total` | `Ref<number>` | 总条数 |

### 2.5 Events

| 事件 | 参数 | 说明 |
|------|------|------|
| `selection-change` | `rows: any[]` | 复选框选择变化 |

### 2.6 使用示例

```vue
<template>
  <PageTable
    ref="tableRef"
    :columns="columns"
    :fetch="fetchBrands"
    :search-fields="searchFields"
    selectable
    @selection-change="handleSelectionChange"
  >
    <template #toolbar>
      <el-button type="primary" @click="handleAdd">新增品牌</el-button>
      <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">批量删除</el-button>
    </template>
    <template #col-logo="{ row }">
      <el-image :src="row.logo" fit="contain" style="width: 60px; height: 40px" />
    </template>
    <template #col-showStatus="{ row }">
      <el-switch :model-value="row.showStatus === 1" @change="(val) => handleStatusChange(row, val)" />
    </template>
    <template #col-actions="{ row }">
      <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
      <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
    </template>
  </PageTable>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PageTable from '@/components/PageTable/index.vue'
import { getBrandPage } from '@/api/product/brand'

const tableRef = ref<InstanceType<typeof PageTable>>()

// 查询函数（PageTable 自动传入 pageNum/pageSize/search 参数）
function fetchBrands(params: Record<string, any>) {
  return getBrandPage(params)
}

// 操作后刷新表格
function handleAddSuccess() {
  tableRef.value?.refresh()
}

// 删除后当前页空则回退上一页
async function handleDelete(row) {
  await deleteBrand(row.id)
  if (tableRef.value?.tableData.length === 1 && tableRef.value.pageNum > 1) {
    tableRef.value.pageNum--
  }
  tableRef.value?.refresh()
}
</script>
```

---

## 三、FormDialog 通用表单弹窗

封装「el-dialog + el-form + 校验 + 提交 loading」标准表单弹窗，使用 TypeScript 泛型保证表单数据类型安全。

### 3.1 设计思路

- **泛型组件**：通过 `<script setup lang="ts" generic="T extends Record<string, any>">` 声明泛型，表单数据类型由父组件传入的 `initialData` 推断
- **数据隔离**：内部维护 `formData` 副本，避免直接修改父组件数据
- **自动重置**：弹窗打开时（`modelValue` 变为 true）自动重置表单为 `initialData` 并清除校验状态
- **提交控制**：`submit` 函数返回 Promise，自动管理 loading 状态；校验失败或提交失败不关闭弹窗

### 3.2 Props

| Prop | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `modelValue` / `v-model` | `boolean` | 必填 | 弹窗可见性 |
| `title` | `string` | 必填 | 弹窗标题 |
| `width` | `string` | `'520px'` | 弹窗宽度 |
| `initialData` | `T` | 必填 | 表单初始数据（编辑时回填） |
| `submit` | `(data: T) => Promise<void>` | 必填 | 提交函数，自动管理 loading |
| `rules` | `FormRules` | - | 表单校验规则 |
| `labelWidth` | `string` | `'100px'` | 表单 label 宽度 |

### 3.3 Slots

| Slot | 说明 | 作用域参数 |
|------|------|-----------|
| `default` | 表单内容 | `{ formModel, formRef }` |

### 3.4 Events

| 事件 | 说明 |
|------|------|
| `update:modelValue` | 弹窗可见性变化（v-model） |
| `success` | 提交成功后触发（弹窗关闭前） |

### 3.5 使用示例

```vue
<template>
  <FormDialog
    v-model="formVisible"
    :title="formTitle"
    :initial-data="formData"
    :submit="handleSubmit"
    :rules="rules"
    width="520px"
  >
    <template #default="{ formModel }">
      <el-form-item label="品牌名称" prop="name">
        <el-input v-model="formModel.name" placeholder="请输入品牌名称" />
      </el-form-item>
      <el-form-item label="品牌介绍" prop="descript">
        <el-input v-model="formModel.descript" type="textarea" :rows="3" />
      </el-form-item>
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import FormDialog from '@/components/FormDialog/index.vue'
import { createBrand, updateBrand } from '@/api/product/brand'

const formVisible = ref(false)
const formData = ref({ name: '', logo: '', descript: '', sort: 0 })

// submit 由 FormDialog 自动管理 loading，无需自己 try/finally
async function handleSubmit(data) {
  if (data.id) {
    await updateBrand(data)
  } else {
    await createBrand(data)
  }
}
</script>
```

### 3.6 弹窗行为约定

- `close-on-click-modal: false`：点击遮罩不关闭，防止误触丢失数据
- `destroy-on-close`：关闭时销毁内容，避免残留状态
- 校验失败（`formRef.validate()` reject）不关闭弹窗
- 提交失败（`submit()` reject）不关闭弹窗，错误已由响应拦截器统一提示

---

## 四、useTable 分页表格 Composable

封装分页表格的状态管理与操作逻辑，适用于需要自行控制分页 UI 或非标准列表场景。

### 4.1 API

```typescript
function useTable<T>(fetchFn: (params: Record<string, any>) => Promise<PageVO<T>>): {
  data: Ref<T[]>
  loading: Ref<boolean>
  total: Ref<number>
  pageNum: Ref<number>
  pageSize: Ref<number>
  searchParams: Ref<Record<string, any>>
  loadData: () => Promise<void>
  handleSearch: () => void      // 重置到第一页并加载
  handleReset: () => void       // 清空搜索条件并加载
  handlePageChange: (page: number) => void
  handleSizeChange: (size: number) => void
  refresh: () => void           // 刷新当前页
}
```

### 4.2 使用场景

- **独立分页列表**：不使用 PageTable 组件，自行渲染 el-table + el-pagination 时
- **树形全量加载**：如分类树（非分页，但需要 loading 状态管理）
- **自定义分页交互**：需要更精细控制分页行为时

> **注意**：使用 PageTable 组件时**不要**同时使用 useTable，两者都会维护分页状态导致冲突。PageTable 内部已自管理分页逻辑。

### 4.3 使用示例（非 PageTable 场景）

```typescript
import { useTable } from '@/composables/useTable'
import { getBrandPage } from '@/api/product/brand'

const { data, loading, total, pageNum, pageSize, handleSearch, handlePageChange } = useTable<BrandVO>(
  (params) => getBrandPage(params)
)
```

---

## 五、useDialog 弹窗 Composable

封装弹窗的可见性、模式（新增/编辑）、表单数据状态。

### 5.1 API

```typescript
function useDialog<T extends Record<string, any>>(defaultData: T): {
  visible: Ref<boolean>
  mode: Ref<'create' | 'update'>
  formData: Ref<T>
  open: (data?: Partial<T>) => void  // 传 data 为编辑模式，不传为新增模式
  close: () => void
}
```

### 5.2 使用示例

```typescript
import { useDialog } from '@/composables/useDialog'

const { visible, mode, formData, open } = useDialog({
  name: '',
  logo: '',
  sort: 0
})

// 新增
function handleAdd() {
  open()
}

// 编辑
function handleEdit(row: BrandVO) {
  open(row)
}
```

> **注意**：当前品牌管理页面使用 FormDialog 组件 + 自行管理 visible/formData，未使用 useDialog。useDialog 适用于不使用 FormDialog 组件、需要自行管理弹窗状态的场景。

---

## 六、request.ts HTTP 请求工具

### 6.1 设计要点

- 响应拦截器自动剥离 `R<T>` 外壳，成功返回 `data`，业务错误弹 `ElMessage.error`
- 网络错误（超时、断网、401）弹 `ElNotification.error`
- 导出 `get/post/put/del` 四个泛型方法，返回 `Promise<T>`（已剥离外壳）

### 6.2 泛型方法

```typescript
// 返回类型 T 是 R<T> 中的 data 部分，调用方无需手动剥离
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T>
```

### 6.3 调用示例

```typescript
import { get, post, del } from '@/utils/request'

// 响应拦截器已剥离 R<T>，直接拿到 data
const tree: CategoryVO[] = await get<CategoryVO[]>('/product/category/tree')
await post<void>('/product/category', formData)
await del<void>(`/product/brand/${id}`)
```

> 完整的拦截器实现与错误处理逻辑见 [infrastructure.md](infrastructure.md) "HTTP 请求层"章节。

---

## 七、tree.ts 树形数据工具

分类管理的拖拽排序计算等场景需要树形数据操作。

### 7.1 函数清单

| 函数 | 签名 | 用途 |
|------|------|------|
| `traverseTree` | `(tree, callback) => void` | 深度优先遍历，callback 返回 false 跳过子节点 |
| `findNode` | `(tree, predicate) => T \| null` | 查找第一个匹配节点 |
| `getNodePath` | `(tree, predicate) => T[]` | 获取从根到目标节点的路径 |
| `isDescendant` | `(tree, ancestorId, targetId, idKey) => boolean` | 判断 target 是否是 ancestor 的子孙（拖拽循环引用检测） |

### 7.2 使用场景

- **分类拖拽排序**：`isDescendant` 检测循环引用（不能把父节点拖到子节点下），`traverseTree` 计算受影响节点
- **分类路径展示**：`getNodePath` 获取"大家电 > 空调"这类层级路径

### 7.3 泛型约束

所有函数都要求节点类型 `T extends { children?: T[] }`（traverseTree/findNode/getNodePath）或 `T extends Record<string, any>`（isDescendant），适配后端返回的树形结构。

---

## 八、计划中的组件

| 组件 | 触发时机 | 设计要点 |
|------|---------|---------|
| ImageUpload | mall-oss 鉴权对接后 | 对接 OSS Presigned URL 直传；支持图片预览、删除、大小限制；替换当前品牌 Logo 的 URL 输入方案 |
| TreeSelect | 按需 | 封装 el-tree-select，支持仅选叶子节点、数据源配置；当前品牌关联分类直接用了 el-tree-select |
| RichTextEditor | SPU 模块 | 商品详情富文本编辑，对接 OSS 图片上传 |
| SvgIcon | 后续 | 自定义 SVG 图标组件，配合 Sprite 图标方案 |
| TagsView | 页面增多后 | 多页签导航，需配合 useTagsViewStore |
