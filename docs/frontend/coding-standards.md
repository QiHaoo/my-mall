# 前端编码规范

> 本规范基于项目实际代码提炼，约束 Vue 3 + TypeScript + Element Plus 的编码风格。
> 视觉与交互规范见 [design-system.md](design-system.md)，通用组件 API 见 [components.md](components.md)。

---

## 一、SFC 结构规范

### 1.1 `<script setup>` 顺序

统一使用 `<script setup lang="ts">`，内部代码按以下顺序组织：

```vue
<script setup lang="ts">
// 1. import（按组：vue → 第三方 → @/别名，组间空行）
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCategoryTree } from '@/api/product/category'
import type { CategoryVO } from '@/api/types/product'
import CategoryForm from './components/CategoryForm.vue'

// 2. props / emits / defineModel / defineExpose
const props = defineProps<{ ... }>()
const emit = defineEmits<{ ... }>()

// 3. 响应式状态（ref / reactive / computed）
const loading = ref(false)
const treeData = ref<CategoryVO[]>([])

// 4. 方法（普通函数，非箭头函数，便于调用栈识别）
function handleAdd() { ... }
async function loadData() { ... }

// 5. 生命周期
onMounted(() => {
  loadData()
})

// 6. defineExpose（如需暴露方法给父组件）
defineExpose({ refresh, loadData })
</script>

<template>
  <!-- 模板 -->
</template>

<style scoped lang="scss">
/* 样式 */
</style>
```

### 1.2 三段顺序

`<script setup>` → `<template>` → `<style scoped lang="scss">`，保持这个顺序不变。

### 1.3 何时用 `generic`

泛型组件在 `<script setup>` 上声明泛型参数：

```vue
<!-- FormDialog 通过 generic 声明表单数据类型 -->
<script setup lang="ts" generic="T extends Record<string, any>">
```

---

## 二、Composition API 使用规范

### 2.1 ref vs reactive

| 场景 | 选择 | 示例 |
|------|------|------|
| 基础类型 | `ref` | `const loading = ref(false)` |
| 对象/数组（整体替换） | `ref` | `const treeData = ref<CategoryVO[]>([])` |
| 需要保持响应式的对象（字段逐个修改） | `reactive` | 表单对象（但本项目用 `ref` + 展开赋值更多） |

> **项目约定**：统一优先用 `ref`，访问时 `.value`，保持一致性。`reactive` 仅在确需深层响应式时使用。

### 2.2 computed

- 只读 computed 用 getter 函数：`const activeMenu = computed(() => route.path)`
- 可写 computed 用 get/set（如双向绑定 props）：见 [FormDialog](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/components/FormDialog/index.vue) 的 `dialogVisible`

### 2.3 watch

```typescript
// watch props 变化重置表单数据
watch(
  () => props.modelValue,
  async (val) => {
    if (val) {
      formData.value = { ...props.initialData }
      await nextTick()
      formRef.value?.clearValidate()
    }
  }
)
```

- 侦听 props / ref 时用 getter 函数 `() => props.xxx`
- 需要 DOM 更新后操作时，用 `await nextTick()`

### 2.4 函数声明

统一用 `function` 声明（非箭头函数），异步用 `async function`：

```typescript
// ✅ 正确
function handleSearch() { ... }
async function loadData() { ... }

// ❌ 避免（箭头函数赋值，调用栈不显示函数名）
const handleSearch = () => { ... }
```

---

## 三、TypeScript 类型规范

### 3.1 ID 字段用 string

后端 Jackson 配置 Long→String 序列化，雪花 ID（19 位）在 JS 中会精度丢失。**所有 ID 字段必须定义为 `string`**：

```typescript
// ✅ 正确
export interface CategoryVO {
  catId: string
  parentCid: string
}

// ❌ 错误（19 位雪花 ID 超过 Number.MAX_SAFE_INTEGER）
export interface CategoryVO {
  catId: number
}
```

### 3.2 PageVO 分页字段为 string

```typescript
export interface PageVO<T> {
  records: T[]
  total: string    // 后端 Long→String，使用时 Number() 转换
  size: string
  current: string
  pages: string
}
```

使用时转换：`total.value = Number(res.total)`

### 3.3 类型定义位置

| 类型 | 位置 | 说明 |
|------|------|------|
| 通用类型（R/PageVO/PageQuery） | `api/types/common.ts` | 跨模块复用 |
| 业务类型（VO/DTO） | `api/types/{module}.ts` | 按后端模块组织 |
| 组件内部类型 | SFC `<script setup>` 内 | 不跨文件共享时 |

### 3.4 interface 命名

- 后端响应实体：`XxxVO`（如 `CategoryVO`、`BrandVO`）
- 请求参数：`XxxDTO` / `XxxQueryDTO`（如 `CategorySaveDTO`、`BrandQueryDTO`）
- 通用结构：语义命名（如 `R<T>`、`PageVO<T>`、`PageQuery`）
- 组件 Props：`defineProps<{ ... }>()` 内联，不单独抽 interface（除非复用）

### 3.5 泛型方法

`request.ts` 导出的 get/post/put/del 用泛型断言返回类型：

```typescript
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request.get(url, config) as unknown as Promise<T>
}
```

> 因为响应拦截器返回 `res.data`（unknown 类型），需通过 `as unknown as Promise<T>` 断言。

---

## 四、API 调用规范

### 4.1 API 函数写法

```typescript
// src/api/product/category.ts
import { get, post, put } from '@/utils/request'
import type { CategoryVO, CategorySaveDTO } from '@/api/types/product'

export function getCategoryTree() {
  return get<CategoryVO[]>('/product/category/tree')
}

export function createCategory(data: CategorySaveDTO) {
  return post<void>('/product/category', data)
}
```

- 用 `get/post/put/del` 泛型方法，不用 `request.get`
- URL 以 `/` 开头（不含网关 `/api` 前缀，baseURL 已配置）
- 泛型参数标注返回类型，无返回值用 `void`

### 4.2 调用与错误处理

```typescript
// 业务代码调用 API
async function loadData() {
  loading.value = true
  try {
    treeData.value = await getCategoryTree()
  } finally {
    loading.value = false
  }
}
```

- **不重复 try/catch 业务错误**：响应拦截器已统一 `ElMessage.error(msg)`，业务代码只需 `try/finally` 处理 loading
- 仅当需要失败后特殊处理（如状态回滚）时才 catch：

```typescript
async function handleStatusChange(row: BrandVO, val: boolean) {
  try {
    await updateBrandShowStatus(row.id, val ? 1 : 0)
    row.showStatus = val ? 1 : 0
    ElMessage.success('状态更新成功')
  } catch {
    // 失败不修改 row.showStatus，el-switch 自动回滚
  }
}
```

---

## 五、组件规范

### 5.1 组件命名

| 类型 | 命名 | 示例 |
|------|------|------|
| 全局通用组件 | PascalCase 目录 + index.vue | `PageTable/index.vue`、`FormDialog/index.vue` |
| 模块专属组件 | PascalCase 文件名 | `CategoryForm.vue`、`BrandRelationDialog.vue` |
| 页面入口 | index.vue | `views/product/category/index.vue` |

### 5.2 Props 定义

用 `defineProps<{ ... }>()` 类型声明 + `withDefaults` 设默认值：

```typescript
const props = withDefaults(
  defineProps<{
    columns: Column[]
    fetch: (params: Record<string, any>) => Promise<PageVO<any>>
    searchFields?: SearchField[]
    rowKey?: string
    selectable?: boolean
  }>(),
  {
    rowKey: 'id',
    selectable: false,
    searchFields: () => []
  }
)
```

### 5.3 Emits 定义

用 `defineEmits<{ ... }>()` 类型声明：

```typescript
const emit = defineEmits<{
  (e: 'selection-change', rows: any[]): void
  (e: 'success'): void
}>()
```

### 5.4 defineExpose

通用组件需暴露方法给父组件 ref 调用时，用 `defineExpose`：

```typescript
defineExpose({ refresh, loadData, tableData, pageNum, pageSize, total })
```

### 5.5 父组件调用子组件方法

```typescript
const tableRef = ref<InstanceType<typeof PageTable>>()

async function handleDelete() {
  await deleteBrand(row.id)
  tableRef.value?.refresh()
}
```

### 5.6 组件通信方式选择

| 场景 | 方式 | 示例 |
|------|------|------|
| 父→子传数据 | props | `<PageTable :columns="columns" />` |
| 子→父通知 | emits | `emit('success')` |
| 父调子方法 | ref + defineExpose | `tableRef.value?.refresh()` |
| 跨组件全局状态 | Pinia store | `useAppStore().toggleSidebar()` |
| 弹窗可见性 | v-model | `<FormDialog v-model="visible" />` |

---

## 六、Composable 规范

### 6.1 命名与位置

- 命名：`useXxx`（如 `useTable`、`useDialog`）
- 位置：`src/composables/` 目录
- 文件名：`useXxx.ts`

### 6.2 写法

```typescript
export function useTable<T>(fetchFn: (params: Record<string, any>) => Promise<PageVO<T>>) {
  const data: Ref<T[]> = ref([])
  const loading = ref(false)

  async function loadData() { ... }
  function refresh() { loadData() }

  return { data, loading, loadData, refresh }
}
```

- 泛型参数放函数签名
- 返回响应式状态（ref）和方法
- 方法用 `function` 声明

### 6.3 何时抽 composable

- 逻辑在 2+ 组件中重复（如分页表格状态管理）
- 组件逻辑过重，需拆分关注点
- **不提前抽象**：只抽已出现的重复，不抽假设的复用

---

## 七、样式规范

### 7.1 SCSS 变量优先

使用 `variables.scss` 中定义的变量，不硬编码值：

```scss
// ✅ 正确
.sidebar {
  background-color: $sidebar-bg;
  width: $sidebar-width;
  font-size: $font-size-lg;
}

// ❌ 避免
.sidebar {
  background-color: #304156;
  width: 210px;
  font-size: 16px;
}
```

> `variables.scss` 通过 Vite `additionalData` 全局注入，所有组件可直接使用，无需 `@use`。

### 7.2 scoped

组件样式必须加 `scoped`：

```vue
<style scoped lang="scss">
.page-table {
  .search-bar { ... }
}
</style>
```

### 7.3 深层选择器

覆盖 Element Plus 内部样式用 `:deep()`：

```scss
:deep(.el-menu) {
  border-right: none;
}
```

### 7.4 工具类

`utilities.scss` 提供常用工具类，优先使用：

| 工具类 | 用途 |
|--------|------|
| `.app-container` | 页面容器 padding 24px |
| `.card-box` | 白色卡片背景 + 圆角 + 阴影 |
| `.flex` / `.flex-center` / `.flex-between` | flex 布局 |
| `.text-ellipsis` | 单行文本截断 |
| `.mt-8` / `.mt-16` / `.mt-24` | margin-top 间距 |

### 7.5 禁止

- ❌ 内联样式 `style="..."`（除非动态计算宽度等无法用 class 的场景）
- ❌ 在 `<style>` 中不加 `scoped`
- ❌ 硬编码颜色/间距值（用 SCSS 变量）

---

## 八、代码风格（Prettier 配置）

项目 `.prettierrc.json` 配置，提交前 `pnpm format` 自动格式化：

| 规则 | 值 | 示例 |
|------|---|------|
| 分号 | 无 | `const a = 1` |
| 引号 | 单引号 | `import { ref } from 'vue'` |
| 缩进 | 2 空格 | - |
| 行宽 | 100 字符 | - |
| 尾逗号 | 无 | `{ a: 1, b: 2 }` |
| 箭头函数参数 | 单参数省略括号 | `x => x + 1` |
| 换行符 | auto | - |

---

## 九、检查清单

提交前自查：

- [ ] SFC 三段顺序：script → template → style
- [ ] 所有 ID 字段定义为 `string`
- [ ] API 函数用 `get/post/put/del` 泛型方法
- [ ] 业务错误不重复 try/catch（拦截器已处理）
- [ ] 函数用 `function` 声明（非箭头函数赋值）
- [ ] 样式用 SCSS 变量，加 `scoped`
- [ ] `pnpm lint` 无报错
- [ ] `pnpm build`（vue-tsc）类型检查通过
