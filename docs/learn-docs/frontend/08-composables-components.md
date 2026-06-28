# 通用组件与 Composables 设计

## 1. Composables 概念回顾

在 [01-Vue3 基础](./01-vue3-basics.md) 的 4.3 节已经讲过：Composable 是一个**约定俗成**的函数，命名以 `use` 开头，用于把可复用的响应式逻辑抽出来。

```ts
// 把「分页表格」逻辑抽成一个函数，多个页面复用
const { data, loading, loadData } = useTable(fetchBrandPage)
```

**后端类比**：Composable 类似 Spring 的 `@Service`——把可复用业务逻辑抽成 Bean，多个组件注入使用。区别在于 Spring 靠 IoC 容器注入，Vue 靠**显式调用 + 解构**。

| | 后端 @Service | 前端 Composable |
|------|------|------|
| 复用单位 | 一个 Bean | 一个函数 |
| 注入方式 | @Autowired（容器注入） | 显式调用 useXxx() |
| 状态 | 单例（全局共享） | 每次调用独立（组件级） |
| 命名约定 | XxxService | useXxx |

> **关键区别**：Spring 的 Service 是单例，所有注入者共享同一实例；Vue 的 Composable 每次调用都返回**一组新的响应式状态**，组件间互不干扰。这是前端「组件级状态」的特性决定的。

本项目在 `src/composables/` 下定义了两个核心 Composable：`useTable`（分页表格）和 `useDialog`（弹窗）。在 `src/components/` 下定义了两个通用组件：`PageTable` 和 `FormDialog`。它们的关系：

```
composables/          components/
├── useTable.ts   ───► PageTable/index.vue   （组件内部自管理状态，未直接用 useTable）
└── useDialog.ts  ───► FormDialog/index.vue  （独立设计，也未直接用 useDialog）
```

> 注意：`useTable` 和 `PageTable` 是**两套独立方案**——useTable 给「想自己控制模板」的页面用，PageTable 给「想要开箱即用列表」的页面用。后文会讲为什么没有让 PageTable 直接复用 useTable。

---

## 2. useTable：分页表格状态管理

文件：`src/composables/useTable.ts`

### 2.1 函数签名与泛型

```ts
export function useTable<T>(fetchFn: (params: Record<string, any>) => Promise<PageVO<T>>) {
```

逐行解析：

- `<T>` —— 泛型参数，代表列表中一条数据的类型（如 `BrandVO`）。调用时由传入的 `fetchFn` 推断，无需手动指定。
- `fetchFn` —— 分页查询函数，由调用方传入。**为什么不把 fetchFn 写死在 composable 里？** 因为每个页面的查询接口不同（品牌页调 `fetchBrandPage`，分类页调 `fetchCategoryPage`）。把「调哪个接口」作为参数传入，composable 只管「怎么管状态、怎么翻页」，职责分离。
- `PageVO<T>` —— 后端统一分页响应结构：

```ts
export interface PageVO<T> {
  records: T[]      // 当前页数据
  total: string     // 总条数（注意是 string，见 2.4）
  size: string
  current: string
  pages: string
}
```

### 2.2 状态变量

```ts
const data: Ref<T[]> = ref([])
const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchParams = ref<Record<string, any>>({})
```

| 变量 | 类型 | 作用 |
|------|------|------|
| `data` | `Ref<T[]>` | 当前页数据列表 |
| `loading` | `Ref<boolean>` | 加载中状态（驱动表格 v-loading） |
| `total` | `Ref<number>` | 总条数（驱动分页器） |
| `pageNum` | `Ref<number>` | 当前页码 |
| `pageSize` | `Ref<number>` | 每页条数 |
| `searchParams` | `Ref<Record<string, any>>` | 搜索条件 |

> **为什么 data 要显式标注 `Ref<T[]>`？** 因为 `ref([])` 推断出的类型是 `Ref<never[]>`，泛型 T 丢失了。显式标注让 `data.value` 拿到正确的 `T[]` 类型，模板里 `row.id` 才有类型提示。

### 2.3 loadData：核心加载方法

```ts
async function loadData() {
  loading.value = true
  try {
    const res = await fetchFn({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...searchParams.value
    })
    data.value = res.records
    total.value = Number(res.total)
  } finally {
    loading.value = false
  }
}
```

几个设计要点：

1. **loading 放在 try/finally**：无论成功失败都要关闭 loading，不能只在成功时关闭。后端类比：相当于 `try-finally` 释放资源。
2. **参数合并**：`{ pageNum, pageSize, ...searchParams }` —— 分页参数固定，搜索参数动态展开。这样调用方只需往 `searchParams` 里塞字段，不用关心分页。
3. **`Number(res.total)`** —— 见下节。

### 2.4 设计决策：为什么 total 用 Number() 转换

后端 Java 的 `Long` 类型在 JavaScript 中会丢失精度（JS Number 最大安全整数是 2^53 - 1，Java Long 是 2^63 - 1）。本项目后端 Jackson 配置了 **Long → String 序列化**，所以 `res.total` 实际是字符串 `"100"`。

```ts
total.value = Number(res.total)  // "100" → 100
```

如果不转换，`el-pagination` 的 `:total` 收到字符串，分页器计算可能出错。这也是 `PageVO` 里 total 字段类型是 `string` 的原因（见 `src/api/types/common.ts` 的注释）。

> **后端类比**：相当于后端接收前端传的 ID 时用 String 接收再转 Long，避免精度丢失。前端这里是反向操作——后端传来 String，前端转回 Number。

### 2.5 搜索、重置、翻页方法

```ts
/** 搜索（重置到第一页） */
function handleSearch() {
  pageNum.value = 1
  loadData()
}
```

搜索时必须回到第一页——否则你可能在第 5 页搜索，结果只有 2 页，表格空白。

```ts
/** 重置搜索条件 */
function handleReset() {
  searchParams.value = {}
  pageNum.value = 1
  loadData()
}
```

重置 = 清空搜索条件 + 回到第一页 + 重新加载。

```ts
/** 页码变化 */
function handlePageChange(page: number) {
  pageNum.value = page
  loadData()
}

/** 每页条数变化 */
function handleSizeChange(size: number) {
  pageSize.value = size
  pageNum.value = 1   // ← 注意：切换每页条数也要回到第一页
  loadData()
}
```

`handleSizeChange` 为什么要回到第一页？假设当前第 3 页每页 10 条，切到每页 50 条，原来的第 3 页（第 21-30 条）在新分页下不存在了，必须回到第一页。

```ts
/** 刷新当前页 */
function refresh() {
  loadData()
}
```

`refresh` 只是 `loadData` 的语义化别名——编辑/删除后调 `refresh()` 比调 `loadData()` 更易读。

### 2.6 设计决策：为什么 searchParams 用 ref 而不是 reactive

```ts
const searchParams = ref<Record<string, any>>({})  // ✅ 用 ref
// 而不是：
// const searchParams = reactive({})  // ❌
```

两个原因：

1. **整体替换**：`handleReset` 里 `searchParams.value = {}` 需要整体替换。`ref` 直接赋新值即可；`reactive` 无法整体替换（只能逐个 delete 属性）。
2. **解构不丢响应式**：返回 `searchParams` 给调用方后，调用方用 `searchParams.value.xxx = yyy` 修改能触发响应式。如果是 reactive，解构后会丢响应式。

> **后端类比**：ref 像 `AtomicReference`（整个引用可替换），reactive 像 `final` 对象（引用不变，内部字段可变）。需要整体替换时用 ref。

### 2.7 返回值

```ts
return {
  data, loading, total, pageNum, pageSize, searchParams,
  loadData, handleSearch, handleReset, handlePageChange, handleSizeChange, refresh
}
```

返回「状态 + 方法」的组合。调用方按需解构：

```ts
const { data, loading, total, pageNum, pageSize, searchParams,
        loadData, handleSearch, handleReset, handlePageChange, handleSizeChange } = useTable(fetchBrandPage)
```

---

## 3. useDialog：弹窗状态管理

文件：`src/composables/useDialog.ts`

### 3.1 泛型约束

```ts
export function useDialog<T extends Record<string, any>>(defaultData: T) {
```

- `T extends Record<string, any>` —— 约束 T 必须是「字符串键的对象」。这样 `{ ...defaultData }` 展开才有意义。
- `defaultData` —— 表单默认数据，新增时使用（如 `{ name: '', logo: '', sort: 0 }`）。

> **为什么要有 defaultData？** 新增时表单不能是空对象 `{}`，否则字段没有初始值，校验和回显都会出问题。每个业务页面的表单结构不同，所以默认数据由调用方传入。

### 3.2 状态变量

```ts
const visible = ref(false)
const mode = ref<'create' | 'update'>('create')
const formData = ref<T>({ ...defaultData }) as ReturnType<typeof ref<T>>
```

| 变量 | 作用 |
|------|------|
| `visible` | 弹窗是否可见 |
| `mode` | 当前模式：新增或编辑 |
| `formData` | 表单数据（双向绑定到表单控件） |

### 3.3 设计决策：为什么 mode 用联合类型

```ts
const mode = ref<'create' | 'update'>('create')
```

`mode` 只能是 `'create'` 或 `'update'` 两个值，用**联合类型**（类似后端枚举）约束：

- 类型安全：写错字符串（如 `'edit'`）编译期就报错
- 语义清晰：模板里 `v-if="mode === 'create'"` 一眼看懂

> **后端类比**：相当于 Java 的 `enum Mode { CREATE, UPDATE }`。TypeScript 的字符串字面量联合类型就是轻量级枚举。

### 3.4 open 方法

```ts
function open(data?: Partial<T>) {
  if (data) {
    mode.value = 'update'
    formData.value = { ...defaultData, ...data }
  } else {
    mode.value = 'create'
    formData.value = { ...defaultData }
  }
  visible.value = true
}
```

- `data?: Partial<T>` —— 参数可选。不传 = 新增，传了 = 编辑。`Partial<T>` 表示 T 的所有字段都变可选（编辑时只传部分字段）。
- 传 data 时是编辑模式，**合并默认数据**后赋值（见 3.6）。
- 不传 data 时是新增模式，用默认数据初始化。

### 3.5 设计决策：为什么 formData 要用 { ...defaultData, ...data } 合并

```ts
formData.value = { ...defaultData, ...data }
```

编辑模式下，后端返回的数据可能**缺少某些字段**（比如新增时才有的默认值字段）。如果直接 `formData.value = data`，缺失字段会是 `undefined`，表单控件可能报错。

合并策略：先铺满默认值，再用实际数据覆盖。保证所有字段都有值。

> **后端类比**：类似 `BeanUtils.copyProperties` 后对 null 字段做兜底处理，避免 NPE。

### 3.6 类型断言说明

```ts
const formData = ref<T>({ ...defaultData }) as ReturnType<typeof ref<T>>
```

这行有个类型断言。原因是 Vue 的 `ref<T>()` 泛型在某些 TS 版本下对复杂泛型推断不准确。`as ReturnType<typeof ref<T>>` 强制断言为 `Ref<T>` 类型，保证 `formData.value` 拿到正确类型。这是项目实战中常见的类型补丁。

### 3.7 close 与返回值

```ts
function close() {
  visible.value = false
}

return { visible, mode, formData, open, close }
```

关闭只设 visible，不清数据——下次 open 会重新赋值，无需提前清。

---

## 4. PageTable：通用分页表格组件

文件：`src/components/PageTable/index.vue`

这是一个**开箱即用**的列表组件，封装了「搜索栏 + 工具栏插槽 + el-table + el-pagination」标准 CRUD 列表模式。

### 4.1 类型定义

```ts
interface Column {
  prop: string
  label: string
  width?: number | string
  align?: 'left' | 'center' | 'right'
  fixed?: boolean | 'left' | 'right'
  /** 是否使用插槽自定义列内容，插槽名为 col-{prop} */
  slot?: boolean
}

interface SearchField {
  prop: string
  label: string
  component: 'input' | 'select'
  placeholder?: string
  options?: { label: string; value: string | number }[]
  width?: string
}
```

- `Column` —— 表格列配置，对应 el-table-column 的属性。`slot` 字段控制该列是否用自定义插槽渲染。
- `SearchField` —— 搜索栏字段配置，支持 input 和 select 两种控件。

> **设计思路**：用配置对象驱动 UI，而不是让调用方手写一堆 el-table-column。后端类比：类似 MyBatis-Plus 的 `Wrapper` 用链式 API 描述查询条件，而非手写 SQL。

### 4.2 Props 定义 + withDefaults

```ts
const props = withDefaults(
  defineProps<{
    columns: Column[]
    fetch: (params: Record<string, any>) => Promise<PageVO<any>>
    searchFields?: SearchField[]
    rowKey?: string
    selectable?: boolean
    defaultPageSize?: number
  }>(),
  {
    rowKey: 'id',
    selectable: false,
    defaultPageSize: 10,
    searchFields: () => []   // ← 引用类型默认值必须用工厂函数
  }
)
```

| Prop | 类型 | 默认值 | 作用 |
|------|------|--------|------|
| `columns` | `Column[]` | 必填 | 表格列配置 |
| `fetch` | `Function` | 必填 | 分页查询函数 |
| `searchFields` | `SearchField[]` | `[]` | 搜索栏字段 |
| `rowKey` | `string` | `'id'` | 行主键字段名 |
| `selectable` | `boolean` | `false` | 是否显示复选框 |
| `defaultPageSize` | `number` | `10` | 默认每页条数 |

> **注意 `searchFields: () => []`**：引用类型（数组、对象）的默认值必须用工厂函数返回，不能直接写 `[]`。这是 Vue `withDefaults` 的要求——否则所有组件实例会共享同一个数组引用。

### 4.3 defineEmits

```ts
const emit = defineEmits<{
  (e: 'selection-change', rows: any[]): void
}>()
```

只暴露一个事件：复选框选择变化。父组件监听这个事件拿选中的行。

### 4.4 内部状态管理

```ts
const tableData = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(props.defaultPageSize)   // ← 从 props 取默认值
const searchParams = ref<Record<string, any>>({})
const tableRef = ref()
```

### 4.5 设计决策：为什么组件内部自管理状态而非用 useTable

这是本组件最关键的设计决策。**PageTable 没有调用 useTable，而是自己维护了一套状态。**

原因：

1. **useTable 的状态是「组件外」的**：useTable 返回的 ref 在调用方组件的 setup 里创建。如果 PageTable 接收 useTable 的返回值作为 props，要传一堆 ref 进来，props 变得多且复杂。
2. **PageTable 要「开箱即用」**：调用方只想传 `fetch` 和 `columns`，不想自己调 useTable 再传状态。组件内部自管理状态，调用方零成本接入。
3. **defineExpose 暴露必要方法**：父组件需要刷新表格时，通过 `ref` 调 `pageTableRef.value.refresh()` 即可，不需要拿到所有内部状态。

```
方案 A（未采用）：PageTable 接收 useTable 返回值
  父组件: const tableState = useTable(fetch) 
  父组件: <PageTable :state="tableState" :columns="..." />
  问题：父组件要管状态，违背「开箱即用」

方案 B（采用）：PageTable 内部自管理状态
  父组件: <PageTable :fetch="fetch" :columns="..." />
  父组件需要刷新: pageTableRef.value.refresh()
  ✅ 父组件零成本
```

> **那 useTable 什么时候用？** 当页面需要「自己控制表格模板」（比如表格结构很特殊，PageTable 的配置化不够用）时，用 useTable 自己搭表格。PageTable 适合标准列表页。两套方案覆盖不同复杂度。

> **后端类比**：PageTable 像 `BaseMapper`——封装了通用 CRUD，子类零成本继承。useTable 像手写 SQL——灵活但要自己写。`BaseMapper` 能覆盖 80% 场景，剩下 20% 特殊需求手写。

### 4.6 loadData 方法

```ts
async function loadData() {
  loading.value = true
  try {
    const res = await props.fetch({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...searchParams.value
    })
    tableData.value = res.records
    total.value = Number(res.total)   // ← 同 useTable，Long→String 精度处理
  } finally {
    loading.value = false
  }
}
```

逻辑和 useTable 的 loadData 完全一致——因为本质都是「调 fetch + 管状态」。这也是为什么有两套代码：组件内部无法直接用 composable（见 4.5 的设计决策）。

### 4.7 onMounted 自动加载

```ts
onMounted(() => {
  loadData()
})
```

组件挂载后自动加载第一页数据。调用方不用手动调 `loadData()`。

### 4.8 defineExpose 暴露方法

```ts
defineExpose({ refresh, loadData, tableData, pageNum, pageSize, total })
```

父组件通过 `ref` 可以调用：

```ts
const pageTableRef = ref()
// 编辑成功后刷新
pageTableRef.value.refresh()
```

暴露的不只是方法，还有 `tableData`、`pageNum` 等状态——父组件有时需要读取当前页数据（比如批量操作选中行）。

### 4.9 模板结构：搜索栏

```html
<div v-if="searchFields.length" class="search-bar card-box">
  <el-form :inline="true" :model="searchParams" class="search-form">
    <el-form-item v-for="field in searchFields" :key="field.prop" :label="field.label">
      <el-input v-if="field.component === 'input'"
        v-model="searchParams[field.prop]" ... @keyup.enter="handleSearch" />
      <el-select v-else-if="field.component === 'select'"
        v-model="searchParams[field.prop]" ...>
        <el-option v-for="opt in field.options" ... />
      </el-select>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
</div>
```

- `v-if="searchFields.length"` —— 没有搜索字段时不渲染搜索栏。
- `v-model="searchParams[field.prop]"` —— 动态绑定，搜索字段的 prop 决定绑定到 searchParams 的哪个 key。
- `@keyup.enter="handleSearch"` —— 回车搜索，提升体验。

### 4.10 模板结构：工具栏插槽 + 表格 + 列插槽

```html
<div class="table-container card-box">
  <!-- 工具栏插槽 -->
  <div v-if="$slots.toolbar" class="toolbar">
    <slot name="toolbar" />
  </div>

  <el-table ref="tableRef" v-loading="loading" :data="tableData" :row-key="rowKey"
            stripe border @selection-change="handleSelectionChange">
    <el-table-column v-if="selectable" type="selection" width="55" />
    <el-table-column v-for="col in columns" :key="col.prop" ... show-overflow-tooltip>
      <template v-if="col.slot" #default="scope">
        <slot :name="`col-${col.prop}`" :row="scope.row" :index="scope.$index" />
      </template>
    </el-table-column>
  </el-table>
```

两个插槽机制：

1. **`toolbar` 插槽**：表格上方的操作区（如「新增」「批量删除」按钮），由父组件填充。
2. **`col-{prop}` 列插槽**：某列需要自定义渲染时（如状态标签、操作按钮），在 Column 配置里设 `slot: true`，父组件用 `#col-xxx` 插槽自定义。

```html
<!-- 父组件用法示例 -->
<PageTable :columns="columns" :fetch="fetchBrandPage">
  <template #toolbar>
    <el-button type="primary" @click="handleAdd">新增</el-button>
  </template>
  <template #col-status="{ row }">
    <el-tag :type="row.status === 1 ? 'success' : 'info'">
      {{ row.status === 1 ? '启用' : '禁用' }}
    </el-tag>
  </template>
</PageTable>
```

> **后端类比**：插槽类似 Spring 的 `Template Method` 模式——父类定义骨架，子类填充扩展点。Vue 用插槽实现「组件骨架 + 扩展点」。

### 4.11 模板结构：分页

```html
<el-pagination
  v-model:current-page="pageNum"
  v-model:page-size="pageSize"
  :total="total"
  :page-sizes="[10, 20, 50]"
  layout="total, sizes, prev, pager, next, jumper"
  background
  @size-change="handleSizeChange"
  @current-change="handlePageChange"
/>
```

- `v-model:current-page` / `v-model:page-size` —— 双向绑定页码和每页条数。
- `@size-change` / `@current-change` —— 变化时触发 loadData。

---

## 5. FormDialog：通用表单弹窗组件

文件：`src/components/FormDialog/index.vue`

封装了 `el-dialog + el-form + 校验 + 提交 loading` 的标准模式。

### 5.1 泛型组件

```ts
<script setup lang="ts" generic="T extends Record<string, any>">
```

这是 Vue 3.3+ 的**泛型组件**语法。`generic="T extends Record<string, any>"` 让整个组件可以用泛型 T，Props 里的 `initialData: T` 和 `submit: (data: T) => Promise<void>` 共享同一个 T。

> **为什么 FormDialog 用泛型组件，PageTable 不用？** FormDialog 的表单数据类型因业务而异（品牌表单、分类表单结构不同），用泛型让 `submit` 函数的参数有精确类型。PageTable 的数据是列表行，用 `any[]` 足够，没必要泛型化。

### 5.2 Props 定义

```ts
const props = withDefaults(
  defineProps<{
    modelValue: boolean          // v-model 可见性
    title: string                // 弹窗标题
    width?: string               // 弹窗宽度
    initialData: T               // 表单初始数据
    submit: (data: T) => Promise<void>  // 提交函数
    rules?: FormRules            // 校验规则
    labelWidth?: string          // label 宽度
  }>(),
  {
    width: '520px',
    labelWidth: '100px'
  }
)
```

| Prop | 作用 |
|------|------|
| `modelValue` | v-model 绑定，控制弹窗显隐 |
| `title` | 弹窗标题 |
| `initialData` | 表单初始数据（编辑时由父组件传入回填数据） |
| `submit` | 提交函数，组件内部调用并管理 loading |
| `rules` | el-form 校验规则 |

### 5.3 设计决策：为什么 submit 是函数 prop 而不是 emit 事件

```ts
submit: (data: T) => Promise<void>   // ✅ 函数 prop
// 而不是：
// emit('submit', formData)          // ❌ 事件
```

原因：

1. **submit 是异步的**：提交要调接口，需要 await。函数 prop 可以 `await props.submit(data)`，组件内部控制 loading。事件无法 await，父组件无法告知「提交完成」。
2. **关注点分离**：父组件传 submit 函数（业务逻辑），FormDialog 管 loading + 校验 + 关闭弹窗（通用逻辑）。父组件不用关心 loading 状态。
3. **类型安全**：`submit: (data: T) => Promise<void>` 参数类型明确。事件的 payload 类型在 defineEmits 里定义，但异步流程管理不如函数直观。

> **后端类比**：函数 prop 类似「策略模式」——父组件传入具体策略（submit 实现），组件在合适时机调用。事件类似「观察者模式」——子组件广播，父组件监听。需要同步返回值的场景用策略（函数 prop），纯通知的场景用观察者（事件）。

### 5.4 v-model 实现

```ts
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'success'): void
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})
```

`v-model` 的本质是 `modelValue` prop + `update:modelValue` emit。用 computed 包装一层：

- `get` —— 读 dialogVisible 时返回 props.modelValue
- `set` —— 写 dialogVisible 时 emit update:modelValue

这样模板里 `v-model="dialogVisible"` 就能双向绑定 el-dialog 的可见性。

### 5.5 内部表单数据 + watch 重置

```ts
const formData = ref<T>({ ...props.initialData }) as ReturnType<typeof ref<T>>

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

**核心逻辑**：每次弹窗打开（modelValue 变 true），重新从 `props.initialData` 复制一份表单数据。

为什么要这样做：

1. **避免修改父组件数据**：`formData` 是 `initialData` 的副本，用户在表单里的编辑不会影响父组件的源数据。取消时不脏数据。
2. **每次打开都重置**：编辑弹窗第二次打开时，要显示最新数据，而不是上次的编辑残留。
3. **clearValidate**：重置数据后，上次的校验错误提示还在，需要清掉。`nextTick` 等 DOM 更新后再清，否则 formRef 可能还没就绪。

### 5.6 设计决策：为什么用 watch 监听 modelValue 而不是用 onMounted

```ts
watch(() => props.modelValue, async (val) => { ... })  // ✅ watch
// 而不是：
// onMounted(() => { formData.value = { ...props.initialData } })  // ❌
```

原因：

1. **弹窗是反复打开关闭的**：onMounted 只在组件首次挂载时触发一次。弹窗第二次打开时不会重新挂载（el-dialog 默认不销毁），onMounted 不触发，表单数据不会重置。
2. **watch 监听 modelValue**：每次显隐状态变化都触发，保证每次打开都有干净的数据。
3. **配合 `destroy-on-close`**：模板里 el-dialog 设了 `destroy-on-close`，但 watch 是更可靠的方案——即使不销毁也能重置。

> **后端类比**：onMounted 像 `@PostConstruct`（只初始化一次），watch 像 `@EventListener`（每次事件都触发）。弹窗的「打开」是反复发生的事件，需要 watch。

### 5.7 handleSubmit：校验 + 提交 + loading

```ts
async function handleSubmit() {
  if (!formRef.value) return
  try {
    if (props.rules) {
      await formRef.value.validate()   // 有规则才校验
    }
    submitting.value = true
    await props.submit(formData.value)  // 调用父组件传入的提交函数
    emit('success')                     // 通知父组件提交成功
    dialogVisible.value = false         // 关闭弹窗
  } catch (err) {
    if (err !== false) {
      // 提交失败的错误已由拦截器处理，这里只阻止关闭
    }
  } finally {
    submitting.value = false
  }
}
```

流程：

1. **校验**：有 rules 才校验（无规则直接跳过，避免空校验报错）。校验失败抛异常，进入 catch。
2. **提交**：开 loading，调 `props.submit(formData.value)`。submit 是异步的，await 等待完成。
3. **成功**：emit `success` 事件（父组件可监听后刷新表格）+ 关闭弹窗。
4. **失败**：校验失败或提交失败都不关闭弹窗，让用户修改后重试。错误提示由 axios 拦截器统一处理（见 09-api-layer）。
5. **finally**：无论成功失败都关 loading。

> **注意 `if (err !== false)`**：el-form 的 `validate()` 校验失败时 reject 的是 `false`，提交接口失败时 reject 的是 Error 对象。这里区分一下，但实际两个分支都没做事——错误已由拦截器处理，这里只负责「不关闭弹窗」。

### 5.8 默认插槽：提供 formModel 和 formRef

```html
<el-form ref="formRef" :model="formData" :rules="rules" :label-width="labelWidth">
  <slot :form-model="formData" :form-ref="formRef" />
</el-form>
```

**作用域插槽**：把 `formData`（作为 form-model）和 `formRef` 暴露给父组件。父组件在插槽里写表单字段，用 `formModel.xxx` 绑定：

```html
<FormDialog v-model="visible" title="新增品牌" :initial-data="form" :submit="handleSubmit">
  <template #default="{ formModel }">
    <el-form-item label="品牌名" prop="name">
      <el-input v-model="formModel.name" />
    </el-form-item>
  </template>
</FormDialog>
```

> **为什么要暴露 formModel？** 因为表单字段双向绑定需要 `v-model="formData.xxx"`，而 formData 在 FormDialog 内部。通过作用域插槽把 formData 传给父组件，父组件才能绑定字段。这是「数据在子组件，模板在父组件」的典型场景。

### 5.9 模板：el-dialog 配置

```html
<el-dialog v-model="dialogVisible" :title="title" :width="width"
           :close-on-click-modal="false" destroy-on-close>
```

- `:close-on-click-modal="false"` —— 禁止点击遮罩关闭，防止误触丢失编辑内容。
- `destroy-on-close` —— 关闭时销毁内容，下次打开是干净状态（配合 watch 双保险）。

---

## 6. tree.ts：树形数据工具函数

文件：`src/utils/tree.ts`

用于商品分类管理的树形数据处理（三级分类树拖拽排序等场景）。

### 6.1 traverseTree：深度优先遍历

```ts
export function traverseTree<T extends { children?: T[] }>(
  tree: T[],
  callback: (node: T, parent: T | null) => boolean | void
): void {
  function walk(nodes: T[], parent: T | null) {
    for (const node of nodes) {
      const result = callback(node, parent)
      if (result === false) continue   // 回调返回 false 跳过子节点
      if (node.children && node.children.length > 0) {
        walk(node.children, node)
      }
    }
  }
  walk(tree, null)
}
```

- 泛型约束 `T extends { children?: T[] }` —— 节点必须有可选的 children 字段（树形结构）。
- `callback` 返回 `false` 时跳过该节点的子节点遍历（剪枝）。
- 递归内部用闭包函数 `walk`，对外只暴露 `traverseTree`。

> **后端类比**：类似树的访问者模式（Visitor），把「遍历算法」和「节点处理」分离。traverseTree 负责遍历，callback 负责处理。

### 6.2 findNode：条件查找

```ts
export function findNode<T extends { children?: T[] }>(
  tree: T[],
  predicate: (node: T) => boolean
): T | null {
  for (const node of tree) {
    if (predicate(node)) return node
    if (node.children && node.children.length > 0) {
      const found = findNode(node.children, predicate)
      if (found) return found
    }
  }
  return null
}
```

深度优先查找第一个匹配节点。`predicate` 是判断函数，类似 `Array.find` 的回调。

### 6.3 getNodePath：获取节点路径

```ts
export function getNodePath<T extends { children?: T[] }>(
  tree: T[],
  predicate: (node: T) => boolean
): T[] {
  const path: T[] = []
  function walk(nodes: T[]): boolean {
    for (const node of nodes) {
      path.push(node)              // 进栈
      if (predicate(node)) return true   // 找到目标，保留路径
      if (node.children && node.children.length > 0) {
        if (walk(node.children)) return true
      }
      path.pop()                   // 没找到，出栈回溯
    }
    return false
  }
  walk(tree)
  return path
}
```

**回溯算法**：维护一个 path 数组，进入节点时 push，离开时 pop。找到目标节点时 path 里就是从根到目标的完整路径。

> **后端类比**：DFS 回溯，类似走迷宫时标记路径，走不通就退回。

用途：分类树面包屑导航——拿到路径后拼接节点名称显示「首页 / 数码 / 手机」。

### 6.4 isDescendant：循环引用检测

```ts
export function isDescendant<T extends Record<string, any>>(
  tree: T[],
  ancestorId: any,
  targetId: any,
  idKey: string
): boolean {
  // 第一步：在树中找到 ancestor 节点
  function findSubtree(nodes: T[]): T | null {
    for (const node of nodes) {
      if (node[idKey] === ancestorId) return node
      if (node.children && node.children.length > 0) {
        const found = findSubtree(node.children)
        if (found) return found
      }
    }
    return null
  }

  const ancestor = findSubtree(tree)
  if (!ancestor || !ancestor.children) return false

  // 第二步：在 ancestor 的子树中查找 target
  function checkDescendant(nodes: T[]): boolean {
    for (const node of nodes) {
      if (node[idKey] === targetId) return true
      if (node.children && node.children.length > 0) {
        if (checkDescendant(node.children)) return true
      }
    }
    return false
  }

  return checkDescendant(ancestor.children)
}
```

**核心应用场景：拖拽排序的循环引用检测。**

商品分类是树形结构，拖拽一个节点 A 到节点 B 下时，必须检查 **A 是否是 B 的祖先**。如果是，把 B 挂到 A 下面会形成循环引用（A → B → A → ...），树结构破坏。

```
拖拽前（A 是 B 的祖先）：
  A
  └── B
      └── C

如果拖拽 A 到 B 下（❌ 非法）：
  B
  └── A
      └── B  ← 循环！
```

检测逻辑：

1. `findSubtree` —— 在树中找到 ancestor（即被拖拽的节点 A）。
2. `checkDescendant` —— 在 A 的子树中查找 target（即目标位置 B）。
3. 如果 B 在 A 的子树里，说明 A 是 B 的祖先，拖拽非法，返回 true。

调用示例（分类拖拽场景）：

```ts
// 拖拽 node 到 target 下之前检查
if (isDescendant(treeData, node.id, target.id, 'catId')) {
  ElMessage.error('不能将节点拖拽到其子节点下')
  return
}
```

> **后端类比**：类似检测有向图是否有环。树是特殊的有向无环图（DAG），拖拽操作可能引入环，isDescendant 就是在操作前做环检测。

> **为什么用 idKey 而不是固定 'id'？** 因为不同业务的树主键字段名不同——分类用 `catId`，菜单用 `menuId`。用参数传入 idKey 让函数更通用。

---

## 7. 总结：通用封装的设计思路

### 7.1 三种复用方式对比

| 方式 | 适用场景 | 本项目实例 |
|------|----------|------------|
| Composable | 复用「响应式逻辑」（状态+方法） | useTable / useDialog |
| 通用组件 | 复用「UI 结构 + 逻辑」 | PageTable / FormDialog |
| 工具函数 | 复用「纯逻辑」（无状态） | tree.ts |

### 7.2 设计原则

1. **职责单一**：useTable 只管分页状态，不管 UI；PageTable 管 UI + 状态；tree.ts 只管纯计算。
2. **配置驱动**：PageTable 用 columns/searchFields 配置驱动渲染，减少重复模板代码。
3. **插槽扩展**：PageTable 的 toolbar 和 col-{prop} 插槽，FormDialog 的默认插槽，保留扩展灵活性。
4. **类型安全**：泛型（useTable<T>、FormDialog generic="T"）保证类型推断，减少 any。

### 7.3 与后端的对照

| 前端 | 后端 | 共同思想 |
|------|------|----------|
| useTable | @Service | 抽取可复用逻辑 |
| PageTable | BaseMapper | 通用 CRUD 封装 |
| FormDialog 函数 prop submit | 策略模式 | 传入具体策略 |
| 插槽 | 模板方法模式 | 骨架 + 扩展点 |
| 联合类型 'create' \| 'update' | enum | 限定取值范围 |
| watch modelValue | @EventListener | 响应反复事件 |

> **核心启示**：前后端的「封装复用」思想是相通的——都是把重复逻辑抽出来，通过参数/配置/继承（插槽）实现扩展。区别只在于前端要处理「响应式」和「UI 渲染」，后端要处理「事务」和「并发」。

---

## 附：文件索引

| 文件 | 路径 | 作用 |
|------|------|------|
| useTable | `src/composables/useTable.ts` | 分页表格状态管理 |
| useDialog | `src/composables/useDialog.ts` | 弹窗状态管理 |
| PageTable | `src/components/PageTable/index.vue` | 通用分页表格组件 |
| FormDialog | `src/components/FormDialog/index.vue` | 通用表单弹窗组件 |
| tree.ts | `src/utils/tree.ts` | 树形数据工具函数 |
| PageVO 类型 | `src/api/types/common.ts` | 分页响应类型定义 |

> **下一篇**：[09-API 层设计](./09-api-layer.md) 将讲解 Axios 封装、拦截器、R&lt;T&gt; 剥离等 API 层设计，与本文的 `fetch` 函数紧密相关。
