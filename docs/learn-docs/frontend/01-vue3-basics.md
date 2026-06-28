# Vue 3 核心知识

> 面向有 Java/Spring 后端经验、但不熟悉前端的开发者。重点讲清最核心、最常用的部分。
> 文中代码示例多数取自本项目的 `mall-admin-frontend/src/`，可直接对照源码阅读。

## 1. Vue 是什么

Vue 是一个**声明式**的 UI 框架：你描述「界面长什么样、跟什么数据绑定」，框架负责「数据变了怎么更新 DOM」。

### 1.1 命令式 vs 声明式

传统原生 JS（命令式）——你得一步步告诉浏览器「怎么做」：

```js
// ❌ 命令式：手动操作 DOM
const btn = document.getElementById('btn')
const countEl = document.getElementById('count')
let count = 0
btn.addEventListener('click', () => {
  count++
  countEl.innerText = `点击了 ${count} 次`  // 手动更新 DOM
})
```

Vue（声明式）——你只描述「数据 → 视图」的映射关系，框架自动同步：

```vue
<!-- ✅ 声明式：只管数据，DOM 由框架更新 -->
<script setup lang="ts">
import { ref } from 'vue'
const count = ref(0)
</script>

<template>
  <button @click="count++">点击</button>
  <span>点击了 {{ count }} 次</span>
</template>
```

数据一变，界面自动更新——这就是 Vue 的核心价值。

### 1.2 后端类比

| | 后端（Spring MVC） | 前端（Vue） |
|------|------|------|
| 思想 | 声明式：`@GetMapping` 描述路由，框架分发请求 | 声明式：`{{ count }}` 描述绑定，框架更新 DOM |
| 对比 | vs 手写 `HttpServlet` + if-else 判断路径 | vs 手写 `addEventListener` + `innerText` |
| 关注点 | 业务逻辑，不关心请求怎么到达 | 数据状态，不关心 DOM 怎么更新 |

> Spring 让你「只写业务，不写 Servlet」；Vue 让你「只管数据，不管 DOM」。两者都是把样板代码下沉到框架。

## 2. 单文件组件（SFC）

Vue 把一个组件的「结构、逻辑、样式」写在一个 `.vue` 文件里，叫单文件组件（Single-File Component）。三段式结构：

```vue
<!-- 1. 模板：描述 HTML 结构（视图） -->
<template>
  <div class="hello">
    <h1>{{ msg }}</h1>
  </div>
</template>

<!-- 2. 脚本：组件的逻辑（数据、方法） -->
<script setup lang="ts">
import { ref } from 'vue'
const msg = ref('你好')
</script>

<!-- 3. 样式：组件的 CSS -->
<style scoped lang="scss">
.hello {
  color: red;
}
</style>
```

三段各自的职责：

| 段 | 职责 | 后端类比 |
|------|------|------|
| `<template>` | 视图结构，类似 HTML，可写 `{{ }}`、指令 | 类似 Thymeleaf 模板 / JSP |
| `<script setup>` | 组件逻辑：数据、方法、生命周期 | 类似 Controller / Service 的方法体 |
| `<style scoped>` | 组件样式，`scoped` 表示只作用于当前组件 | 类似组件级私有 CSS 命名空间 |

**`scoped` 的作用**：给当前组件的 HTML 加上 `data-v-xxx` 属性，样式只命中本组件元素，不会污染其他组件。相当于「组件私有样式」。

> 项目实例：`src/layouts/components/Sidebar.vue` 就是一个标准 SFC，template 里渲染菜单，script 里读路由配置，style 里定义侧边栏样式。

## 3. 响应式系统（最重要）

「数据变了，界面自动更新」依赖的就是**响应式系统**。这是 Vue 的灵魂，必须吃透。

### 3.1 ref：包裹任意值成为响应式

`ref()` 把一个值（基本类型 / 对象）包装成响应式。读写在 JS 里要加 `.value`，在模板里不用。

```ts
import { ref } from 'vue'

const count = ref(0)       // 数字
const name = ref('Tom')    // 字符串

count.value++              // ✅ JS 里必须 .value
console.log(count.value)   // 0 → 1

// 模板里直接用 {{ count }}，不用 .value
```

**项目实例**（`src/stores/app.ts`）——侧边栏折叠状态：

```ts
export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value   // ✅ JS 里用 .value
    localStorage.setItem('sidebarCollapsed', String(sidebarCollapsed.value))
  }

  return { sidebarCollapsed, toggleSidebar }
})
```

模板里直接用 `appStore.sidebarCollapsed`，不用 `.value`（见 `Sidebar.vue`、`Navbar.vue`）。

### 3.2 为什么 ref 需要 .value

后端开发者常觉得 `.value` 很别扭。原因：

- `ref` 可以包装**基本类型**（number、string、boolean）。基本类型是按值传递的，没法被「代理」。
- 所以 Vue 用一个对象 `{ value: T }` 把它包起来，你改的是 `.value` 属性，Vue 才能拦截到变化。
- 模板里 Vue 自动解包（unwrap），所以不用写 `.value`，是个语法糖。

```
ref(0)  →  { value: 0 }   ← Proxy 代理这个对象
              ↑
        改 .value，Vue 拦截到，触发更新
```

> 记忆口诀：**JS 里写 `.value`，模板里不写**。

### 3.3 reactive：包裹对象成为响应式

`reactive()` 用于对象（含数组），返回对象的代理副本，**不需要 `.value`**。

```ts
import { reactive } from 'vue'

const state = reactive({
  count: 0,
  user: { name: 'Tom' }
})

state.count++              // ✅ 直接用，不用 .value
state.user.name = 'Jerry'  // ✅ 嵌套属性也是响应式
```

### 3.4 ref vs reactive 怎么选

| | `ref` | `reactive` |
|------|------|------|
| 适用类型 | 任意（基本类型 + 对象） | 仅对象 |
| 访问方式 | JS 里需 `.value` | 直接访问 |
| 重新赋值 | `xxx.value = newVal` ✅ | `state = newObj` ❌ 丢失响应式 |
| 推荐 | 默认首选 | 适合一组相关状态的表单对象 |

**经验法则**：默认用 `ref`，它对基本类型和对象都通用，且能整体替换。`reactive` 适合「一个对象多个字段」的场景（如复杂表单），但**不能整体重新赋值**——这会丢失响应式。

```ts
// ❌ reactive 整体替换，丢失响应式
const state = reactive({ list: [] })
state = { list: [1, 2] }   // 错！state 不再是代理

// ✅ 改用 ref，或改 reactive 的属性
const list = ref([])
list.value = [1, 2]        // 对，整体替换 OK
```

> 项目里基本都用 `ref`，例如 `PageTable` 里的 `tableData`、`loading`、`total`、`pageNum` 全是 `ref`，统一风格。

### 3.5 computed：计算属性

`computed` 基于「已有响应式数据」派生出新的值，且**有缓存**——依赖不变就不重算。

```ts
import { ref, computed } from 'vue'

const price = ref(100)
const count = ref(2)

// 只读计算属性
const total = computed(() => price.value * count.value)
// total.value === 200；price/count 没变时，多次访问 total 不重算
```

**可写 computed**（带 get/set）——常用于 v-model 双向绑定代理：

```ts
// 项目实例：src/components/FormDialog/index.vue
const dialogVisible = computed({
  get: () => props.modelValue,                  // 读：取父组件传入的值
  set: (val) => emit('update:modelValue', val)  // 写：通知父组件更新
})
```

**项目实例**（`src/views/product/brand/index.vue`）——从选中行派生 id 数组：

```ts
const selectedRows = ref<BrandVO[]>([])
const selectedIds = computed(() => selectedRows.value.map((r) => r.id))
// selectedRows 变了，selectedIds 自动更新；批量删除按钮的 disabled 绑定它
```

**computed vs 方法的区别**：方法每次调用都重算，computed 有缓存。在模板里频繁使用时，computed 性能更好。

### 3.6 watch：侦听器

`watch` 用来「监听某个数据变化后，执行副作用」（发请求、操作 DOM 等）。

```ts
import { ref, watch } from 'vue'

const keyword = ref('')

// 监听单个 ref
watch(keyword, (newVal, oldVal) => {
  console.log(`从 ${oldVal} 变成 ${newVal}`)
  // 这里可以发搜索请求
})

// 监听多个数据源
watch([keyword, type], ([newKw, newType]) => { ... })
```

监听 `props` 或 `reactive` 的某个属性，要写成 getter 函数：

```ts
// 项目实例：src/components/FormDialog/index.vue
watch(
  () => props.modelValue,          // ✅ 监听 props 的属性，写成函数
  async (val) => {
    if (val) {
      formData.value = { ...props.initialData }
      await nextTick()
      formRef.value?.clearValidate()
    }
  }
)
```

### 3.7 watchEffect：自动收集依赖

`watchEffect` 不用指定监听谁，函数体里用到哪些响应式数据，就自动追踪哪些。

```ts
import { ref, watchEffect } from 'vue'

const count = ref(0)
watchEffect(() => {
  // 自动追踪 count，count 变了就重新执行
  console.log(`当前 count: ${count.value}`)
})
// 注意：watchEffect 会立即执行一次（用于收集依赖）
```

| | `watch` | `watchEffect` |
|------|------|------|
| 依赖 | 显式指定 | 自动收集 |
| 立即执行 | 默认否（可配 `immediate: true`） | 是 |
| 能否拿到旧值 | 能 | 不能 |
| 场景 | 需要新旧值对比 / 明确监听某个值 | 只要数据变就执行副作用 |

### 3.8 响应式原理简述（Proxy vs defineProperty）

**Vue 2** 用 `Object.defineProperty` 给对象属性加 getter/setter：

```
对象 ─┬─ 属性A  (defineProperty 劫持 get/set)
      └─ 属性B  (defineProperty 劫持 get/set)
```

痛点：
- **新增属性**不响应（要 `Vue.set`）
- **删除属性**不响应
- **数组**要重写 `push/pop` 等方法才能响应
- 只能劫持已存在的属性，深层对象要递归遍历

**Vue 3** 改用 ES6 `Proxy` 代理整个对象：

```js
const proxy = new Proxy(target, {
  get(obj, key) { /* 读取时收集依赖 */ return obj[key] },
  set(obj, key, val) { obj[key] = val; /* 触发更新 */ return true }
})
```

Proxy 代理的是整个对象，所以：
- 新增 / 删除属性都响应
- 数组直接支持
- 按需代理（访问到才递归代理深层），性能更好

```
原始对象  ──Proxy代理──►  拦截所有操作（读/写/删除/遍历）
                            │
                            └─ 自动追踪依赖、触发更新
```

> 后端类比：`Object.defineProperty` 像「给每个字段单独加 AOP 切面」，新增字段没切面；`Proxy` 像「给整个对象加动态代理」，所有操作都经过代理。

## 4. Composition API

### 4.1 为什么要有 Composition API

Vue 2 的 **Options API** 按选项类型组织代码：`data`、`methods`、`computed`、`watch` 分开放。

```js
// Options API：同一功能的代码被拆散到不同选项
export default {
  data() { return { count: 0, name: '' } },           // count 和 name 在一起
  methods: { increment() {...}, fetchName() {...} },  // 两个功能的方法混在一起
  computed: { doubleCount() {...} },                   // 计算属性又分一处
  watch: { count() {...} }                             // 侦听器又一处
}
```

痛点：**同一功能的逻辑被拆散到 4-5 个选项里**，组件一大就「跳来跳去读代码」，相关逻辑无法复用。

**Composition API** 按「功能」组织代码——一个功能的「数据+方法+计算+侦听」放一起：

```ts
// Composition API：分页功能的所有逻辑聚在一起
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
async function loadData() { ... }
function handlePageChange(p) { pageNum.value = p; loadData() }
```

更关键的是：可以把一整块逻辑**抽成可复用的函数**（Composable，见 4.3）。

### 4.2 `<script setup>` 语法糖

`<script setup>` 是 Composition API 的编译期语法糖，是现在的**推荐写法**。对比普通 `<script>`：

```vue
<!-- ❌ 旧写法：要 export default，手动 return 暴露 -->
<script lang="ts">
import { ref } from 'vue'
export default {
  setup() {
    const count = ref(0)
    function inc() { count.value++ }
    return { count, inc }   // 要 return 才能在模板用
  }
}
</script>

<!-- ✅ script setup：顶层变量自动暴露给模板 -->
<script setup lang="ts">
import { ref } from 'vue'
const count = ref(0)
function inc() { count.value++ }   // 不用 return，模板直接能用
</script>
```

好处：
- 顶层声明的变量、函数、import 的组件，**自动可在模板使用**，不用 return
- 更好的 TypeScript 类型推导
- 编译后体积更小、性能更好

> 项目里所有 `.vue` 都用 `<script setup lang="ts">`，如 `Sidebar.vue`、`PageTable/index.vue`。

### 4.3 组合式函数（Composables）

Composable 是一个**约定俗成**的函数（非 API），命名以 `use` 开头，用于把可复用的响应式逻辑抽出来。后端类比：类似 Spring 的 `@Service`——把可复用业务逻辑抽成 Bean，多个组件注入使用。

**项目实例**：`src/composables/useTable.ts` 抽取了「分页表格」的通用逻辑：

```ts
export function useTable<T>(fetchFn: (params) => Promise<PageVO<T>>) {
  const data = ref<T[]>([])
  const loading = ref(false)
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const searchParams = ref<Record<string, any>>({})

  async function loadData() {
    loading.value = true
    try {
      const res = await fetchFn({ pageNum: pageNum.value, pageSize: pageSize.value, ...searchParams.value })
      data.value = res.records
      total.value = Number(res.total)
    } finally {
      loading.value = false
    }
  }

  function handleSearch() { pageNum.value = 1; loadData() }
  function handleReset() { searchParams.value = {}; pageNum.value = 1; loadData() }
  function refresh() { loadData() }

  return { data, loading, total, pageNum, pageSize, searchParams, loadData, handleSearch, handleReset, refresh }
}
```

使用方就像调普通函数，拿到一组响应式数据和方法：

```ts
const { data, loading, loadData, handleSearch } = useTable(fetchBrandPage)
```

**约定**：
- 命名以 `use` 开头（`useTable`、`useDialog`、`useMouse`）
- 只在 `setup` 顶层调用（保证响应式上下文）
- 返回响应式数据 + 操作方法

> 对比 Options API 时代的 mixin：mixin 是「隐式合并」，多个 mixin 命名冲突难追踪；Composable 是「显式调用、显式解构」，来源清晰、可重命名。类似 Spring 的「显式注入 Bean」优于「隐式继承」。

## 5. 组件通信

组件化开发下，组件间需要传数据、调方法。Vue 的核心原则是**单向数据流**：父传子用 props，子传父用 emits。

```
   父组件
     │ props（数据下传）
     ▼
   子组件
     │ emits（事件上传）
     ▼
   父组件响应事件
```

### 5.1 Props：父传子

父组件通过属性传值，子组件用 `defineProps` 声明接收。

```vue
<!-- 子组件：src/views/product/category/components/CategoryNode.vue -->
<script setup lang="ts">
defineProps<{
  data: CategoryVO      // 父组件传进来的分类数据
}>()
</script>

<template>
  <span>{{ data.name }}</span>
</template>
```

```vue
<!-- 父组件传值 -->
<CategoryNode :data="item" />
```

带默认值用 `withDefaults`（项目 `PageTable/index.vue`）：

```ts
const props = withDefaults(
  defineProps<{
    columns: Column[]
    fetch: (params: Record<string, any>) => Promise<PageVO<any>>
    rowKey?: string
    selectable?: boolean
    defaultPageSize?: number
  }>(),
  {
    rowKey: 'id',
    selectable: false,
    defaultPageSize: 10
  }
)
```

> 注意：props 是**只读**的，子组件不能直接改父传的值（单向数据流）。要改就通过 emits 通知父组件。

### 5.2 Emits：子传父

子组件用 `defineEmits` 声明事件，父组件用 `@事件名` 监听。

```vue
<!-- 子组件：CategoryNode.vue -->
<script setup lang="ts">
const emit = defineEmits<{
  (e: 'add', data: CategoryVO): void
  (e: 'edit', data: CategoryVO): void
  (e: 'delete', data: CategoryVO): void
}>()
</script>

<template>
  <el-button @click.stop="emit('add', data)">新增</el-button>
</template>
```

```vue
<!-- 父组件监听 -->
<CategoryNode
  :data="item"
  @add="handleAdd"
  @edit="handleEdit"
/>
```

**项目实例**（`PageTable/index.vue`）——复选框变化通知父组件：

```ts
const emit = defineEmits<{
  (e: 'selection-change', rows: any[]): void
}>()

function handleSelectionChange(rows: any[]) {
  emit('selection-change', rows)
}
```

### 5.3 defineExpose：暴露方法给父组件

`<script setup>` 默认对外**封闭**，父组件拿不到子组件的变量/方法。用 `defineExpose` 显式暴露。

**项目实例**（`PageTable/index.vue`）——暴露 refresh 给父组件调用：

```ts
function refresh() { loadData() }

defineExpose({ refresh, loadData, tableData, pageNum, pageSize, total })
```

父组件通过模板 ref 拿到子组件实例，调用暴露的方法：

```vue
<!-- 父组件：src/views/product/brand/index.vue -->
<script setup lang="ts">
const tableRef = ref<InstanceType<typeof PageTable>>()

function handleFormSuccess() {
  tableRef.value?.refresh()   // ✅ 调用子组件暴露的 refresh
}
</script>

<template>
  <PageTable ref="tableRef" ... />
</template>
```

**项目实例**（`BrandForm.vue`）——暴露打开弹窗的方法：

```ts
defineExpose({ openCreate, openEdit })
```

```ts
// 父组件调用
const formRef = ref<InstanceType<typeof BrandForm>>()
formRef.value?.openCreate()   // 父组件主动打开子组件弹窗
```

### 5.4 v-model：双向绑定

`v-model` 是 props + emits 的语法糖，实现「父子双向同步」。

**组件上的 v-model** 本质：

```vue
<!-- 这两行等价 -->
<MyComp v-model="visible" />
<MyComp :model-value="visible" @update:model-value="visible = $event" />
```

子组件接收 `modelValue` prop，emit `update:modelValue` 事件：

```ts
// 项目实例：src/components/FormDialog/index.vue
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

// 用可写 computed 代理，模板里 v-model 直接绑定它
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})
```

父组件用法（`brand/index.vue`）：

```vue
<BrandForm v-model="formVisible" @success="handleFormSuccess" />
```

`formVisible` 在父子间双向同步——子组件关闭弹窗时 set `dialogVisible`，触发 emit，父组件的 `formVisible` 也变 `false`。

**原生表单的 v-model** 更简单，直接绑定 ref：

```vue
<el-input v-model="formData.name" />
<!-- 等价于 :model-value="formData.name" @update:model-value="formData.name = $event" -->
```

### 5.5 provide / inject：跨层级

props/emits 只能父子传。跨多层组件时用 `provide`（祖先提供）+ `inject`（后代注入）。

```ts
// 祖先组件：provide
import { provide, ref } from 'vue'
const theme = ref('dark')
provide('theme', theme)   // 提供给所有后代

// 任意后代组件：inject
import { inject } from 'vue'
const theme = inject('theme', 'light')   // 第二个参数是默认值
```

```
祖先 provide('theme', ...)
   └─ 子组件 A（不关心 theme）
       └─ 子组件 B
           └─ 子组件 C  inject('theme')  ✅ 能拿到，不用层层 props
```

> 后端类比：类似 Spring 的 `ApplicationContext`——祖先放进去，任何后代都能取，不用一层层传参。
> 注意：`provide/inject` 主要用于**组件库/高阶场景**，业务里优先用 Pinia 状态管理（项目用 `stores/app.ts` 管理全局状态）。

## 6. 生命周期

组件从创建、挂载、更新到销毁的过程，Vue 在各阶段提供钩子函数。

```
创建响应式状态 ──► onBeforeMount ──► onMounted(DOM 已渲染)
                                         │
                                         ▼
                                    onBeforeUpdate ──► onUpdated
                                         ▲                  │
                                         └──────┬───────────┘
                                                │ （数据反复变化）
                                     onBeforeUnmount ──► onUnmounted
```

常用钩子（`<script setup>` 里直接调用）：

```ts
import { onMounted, onUpdated, onUnmounted } from 'vue'

onMounted(() => {
  console.log('组件已挂载，DOM 可访问')
  // 常在此发请求、加定时器、初始化第三方库
})

onUpdated(() => {
  console.log('组件更新完成')
})

onUnmounted(() => {
  console.log('组件卸载')
  // 常在此清定时器、取消订阅、释放资源
})
```

**项目实例**（`PageTable/index.vue`）——挂载时自动加载数据：

```ts
onMounted(() => {
  loadData()
})
```

### 后端类比

| Vue 生命周期 | Spring Bean 生命周期 | 用途 |
|------|------|------|
| setup（创建） | 构造方法 / `@PostConstruct` 前 | 初始化响应式状态 |
| onMounted | `@PostConstruct` | 初始化完成，可操作 DOM / 发请求 |
| onUpdated | — | 数据变化后处理 |
| onUnmounted | `@PreDestroy` | 释放资源、清理副作用 |

> 没有 `onCreated`——因为 `<script setup>` 的顶层代码就相当于「创建」阶段，直接写同步代码即可。

## 7. 条件渲染与列表渲染

### 7.1 v-if vs v-show

```vue
<!-- v-if：真正从 DOM 移除/添加 -->
<div v-if="show">内容</div>

<!-- v-show：始终渲染，用 display:none 切换 -->
<div v-show="show">内容</div>
```

| | `v-if` | `v-show` |
|------|------|------|
| 实现 | 增删 DOM 元素 | `display: none` 切换 |
| 初始渲染 | false 时不渲染（惰性） | 始终渲染 |
| 切换开销 | 高（增删 DOM） | 低（改样式） |
| 首次开销 | 低（false 时不渲染） | 高（始终渲染） |
| 适用 | 不频繁切换 | 频繁切换 |

**项目实例**（`PageTable/index.vue`）——搜索栏存在才渲染：

```vue
<div v-if="searchFields.length" class="search-bar card-box">
  ...
</div>
```

**项目实例**（`Sidebar.vue`）——根据折叠状态切换图标：

```vue
<Fold v-if="!appStore.sidebarCollapsed" />
<Expand v-else />
```

### 7.2 v-for + key

`v-for` 遍历数组/对象，**必须加 `key`**（帮助 Vue 高效复用 DOM、避免状态错乱）。

```vue
<!-- 项目实例：PageTable/index.vue 渲染列 -->
<el-table-column
  v-for="col in columns"
  :key="col.prop"        <!-- ✅ 用唯一字段做 key -->
  :prop="col.prop"
  :label="col.label"
/>
```

```vue
<!-- 项目实例：Sidebar.vue 渲染菜单 -->
<el-menu-item
  v-for="item in menuItems"
  :key="item.path"
  :index="'/' + item.path"
>
  {{ item.meta?.title }}
</el-menu-item>
```

**key 的要求**：
- 唯一且稳定（不要用数组索引 `index`，除非列表纯展示无增删改）
- 用数据的业务主键（`id`、`path`、`prop`）

> ❌ `:key="index"` 在有增删、排序时会导致渲染错乱，因为 index 会变化。

### 7.3 v-if 和 v-for 不要一起用

```vue
<!-- ❌ 不要这样 -->
<li v-for="item in list" v-if="item.show" :key="item.id">

<!-- ✅ 用 computed 先过滤 -->
<li v-for="item in visibleList" :key="item.id">

<script setup>
const visibleList = computed(() => list.value.filter(i => i.show))
</script>
```

原因：`v-if` 和 `v-for` 同时在同一元素上，优先级容易混淆（Vue 3 中 v-if 优先级更高，会拿不到 v-for 的变量）。

## 8. 模板引用（ref）

模板 ref 用来「拿到真实的 DOM 元素或子组件实例」，在需要直接操作时用。

### 8.1 获取 DOM 元素

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'

const inputRef = ref<HTMLInputElement>()   // 变量名要和模板 ref 一致

onMounted(() => {
  inputRef.value?.focus()    // ✅ 挂载后才能拿到 DOM
})
</script>

<template>
  <input ref="inputRef" />
</template>
```

### 8.2 获取子组件实例

配合 `defineExpose`，父组件能调用子组件暴露的方法（见 5.3）：

```ts
// 项目实例：brand/index.vue
const tableRef = ref<InstanceType<typeof PageTable>>()
const formRef = ref<InstanceType<typeof BrandForm>>()

function handleEdit(row: BrandVO) {
  formRef.value?.openEdit(row)   // 调子组件暴露的方法
}
function handleFormSuccess() {
  tableRef.value?.refresh()
}
```

```vue
<template>
  <PageTable ref="tableRef" ... />
  <BrandForm ref="formRef" v-model="formVisible" />
</template>
```

### 8.3 使用要点

- **名字要对应**：`const xxxRef = ref()` 与模板里 `ref="xxxRef"` 必须同名（Vue 3.5+ 自动绑定，旧版需同名字段）。
- **挂载后才有值**：`ref()` 初始是 `undefined`，`onMounted` 之后才能访问。
- **别滥用**：优先用 props/emits 数据驱动，只有「必须直接操作 DOM」（focus、滚动、图表初始化）或「调用子组件方法」时才用 ref。

---

## 速查总结

| 知识点 | 核心 API | 一句话记忆 |
|------|------|------|
| 响应式 | `ref` / `reactive` | 默认 ref，JS 里加 `.value` |
| 计算属性 | `computed` | 派生数据，有缓存 |
| 侦听器 | `watch` / `watchEffect` | 数据变后执行副作用 |
| 脚本写法 | `<script setup>` | 顶层自动暴露给模板 |
| 逻辑复用 | Composable（`useXxx`） | 把响应式逻辑抽成函数 |
| 父传子 | `defineProps` | props 只读 |
| 子传父 | `defineEmits` | 单向数据流 |
| 暴露方法 | `defineExpose` | 父组件 ref 调用 |
| 双向绑定 | `v-model` | props + emits 语法糖 |
| 跨层级 | `provide` / `inject` | 祖先给后代，业务优先用 Pinia |
| 生命周期 | `onMounted` 等 | 挂载发请求，卸载清资源 |
| 条件渲染 | `v-if` / `v-show` | 频繁切换用 show |
| 列表渲染 | `v-for` + `key` | key 用唯一业务主键 |
| 模板引用 | `ref="xxx"` | 拿 DOM/子组件实例 |
