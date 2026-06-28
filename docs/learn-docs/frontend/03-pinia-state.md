# Pinia 状态管理

> 写给有后端经验、刚开始写 Vue 的同学。假设你已经会基本的组件写法。

## 1. 什么是状态管理

### 1.1 先说问题：组件间怎么共享数据

Vue 是组件化框架，一个页面被拆成几十个组件。每个组件有自己的数据（`ref` / `reactive`），默认互相看不到。

```
┌─────────────────────────────────────────────┐
│  Layout                                      │
│  ┌───────────┐  ┌─────────────────────────┐ │
│  │  Sidebar   │  │  Navbar                 │ │
│  │  (要判断   │  │  (点击按钮要改折叠状态) │ │
│  │   折叠?)   │  │                         │ │
│  └───────────┘  └─────────────────────────┘ │
└─────────────────────────────────────────────┘
```

比如「侧边栏是否折叠」这个状态：

- `Navbar` 里有个按钮要**修改**它
- `Sidebar` 里要根据它**决定**菜单宽度
- `Layout` 里可能还要根据它**调整**主体区域 margin

如果只用组件局部状态，你得通过 `props` 往下传、`emit` 往上抛，层级一深就是传说中的 **prop drilling**（属性逐层透传），写起来烦、改起来乱。

### 1.2 类比后端的 Session / 上下文

对后端同学来说，状态管理可以这么类比：

| 后端概念 | 前端对应 | 说明 |
|------|------|------|
| `HttpSession` | Pinia Store | 跨请求/跨组件共享的数据容器 |
| `ThreadLocal` | 组件局部 `ref` | 只在当前作用域生效 |
| `ApplicationContext` | Pinia Store | 全局单例，随处可取 |
| Session 持久化到 Redis | Store 持久化到 localStorage | 刷新/重启后还能恢复 |

区别在于：后端的 Session 是「每个用户一份」，存在服务端；前端的 Store 是「每个浏览器标签页一份」，存在用户机器上，刷新页面就重新创建。

### 1.3 什么数据该放 Store

一句话原则：**多个组件需要共享、且不是某次接口请求的临时结果**，才放 Store。

| 放 Store | 不放 Store |
|------|------|
| 侧边栏折叠、主题、语言 | 某个页面的商品列表（接口拿一次就完） |
| 用户登录态（token、权限） | 表单输入中的临时值 |
| 全局通知数量 | 弹窗开关（只在一个组件里用） |

详细判断见第 5 节。

## 2. Pinia 基础

Pinia 是 Vue 官方推荐的状态管理库（Vuex 的继任者）。核心概念三个：**State**（状态）、**Getters**（计算属性）、**Actions**（方法）。

### 2.1 安装与初始化

项目里已经在 `main.ts` 装好了：

```typescript
// mall-admin-frontend/src/main.ts
import { createPinia } from 'pinia'

const app = createApp(App)
app.use(createPinia())   // 注册 Pinia 插件，全应用可用
```

### 2.2 State、Getters、Actions 三件套

Pinia 的核心就三个概念，对应 Vue 组件里你熟悉的东西：

| Pinia 概念 | 组件里对应 | 作用 |
|------|------|------|
| State | `ref` / `reactive` | 存数据 |
| Getters | `computed` | 派生数据（依赖 State 算出来的） |
| Actions | `function` | 改数据的方法（可异步） |

**Getters** 适合放「由 State 推导出来、会被多处用」的值，避免在每个组件里重复算：

```typescript
export const useCartStore = defineStore('cart', () => {
  const items = ref<{ name: string; price: number; qty: number }[]>([])

  // Getter：购物车总价，items 一变自动重算
  const totalPrice = computed(() =>
    items.value.reduce((sum, i) => sum + i.price * i.qty, 0)
  )

  // Getter 之间还能互相引用
  const itemCount = computed(() => items.value.length)
  const isEmpty = computed(() => itemCount.value === 0)

  return { items, totalPrice, itemCount, isEmpty }
})
```

类比后端：Getter 像一个「只读计算字段」，类似 JPA 实体里 `@Transient` 的派生属性，但它是响应式的——依赖的 State 一变，自动重算。

### 2.3 定义 Store：setup 语法 vs options 语法

Pinia 支持两种写法。项目用的是 **setup 语法**（组合式 API 风格，和 `<script setup>` 一致，推荐）。

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// ✅ setup 语法：像写组件一样写 Store
export const useCounterStore = defineStore('counter', () => {
  const count = ref(0)                    // State
  const double = computed(() => count.value * 2)  // Getter
  function increment() {                  // Action
    count.value++
  }
  return { count, double, increment }     // 记得 return，外部才能访问
})
```

对比 **options 语法**（老风格，类似 Vuex）：

```typescript
// ⚠️ options 语法：能用，但不推荐新代码用
export const useCounterStore = defineStore('counter', {
  state: () => ({ count: 0 }),
  getters: { double: (state) => state.count * 2 },
  actions: { increment() { this.count++ } }
})
```

| | setup 语法 | options 语法 |
|------|------|------|
| 写法 | 函数 + `ref`/`computed` | 配置对象 |
| 与组件一致性 | 和 `<script setup>` 一致 | 类似 Vuex 老风格 |
| 类型推导 | 更好 | 一般 |
| 推荐 | ✅ 新代码用 | 了解即可 |

**记住一点**：setup 语法里，`return` 出去的东西外部才能用，没 return 的就是 Store 内部私有变量。这是和 options 语法最大的区别。

### 2.4 在组件中使用 Store

```vue
<script setup lang="ts">
import { useCounterStore } from '@/stores/counter'
import { storeToRefs } from 'pinia'

const counterStore = useCounterStore()   // 拿到 store 实例

// 直接解构会丢失响应性！必须用 storeToRefs
const { count, double } = storeToRefs(counterStore)  // ✅ 保持响应性
const { increment } = counterStore                    // 方法可以直接解构
</script>

<template>
  <button @click="increment">+1</button>
  <p>{{ count }} / {{ double }}</p>
</template>
```

**坑点**：State 和 Getter 是响应式的，直接解构会「断开」响应性（拿到的是普通值）。要用 `storeToRefs()` 包一层。**Action 是普通函数，可以直接解构**。

### 2.5 Store 实例自带的几个 API

Pinia 给每个 store 实例挂了几个内置方法，偶尔会用：

| 方法 | 作用 | 类比 |
|------|------|------|
| `$reset()` | 把 State 重置为初始值 | 后端「重置到默认配置」 |
| `$patch(state)` | 批量改多个 State | 一次 update 多个字段 |
| `$subscribe(cb)` | 监听 State 变化 | 类似监听器 / 观察者 |

```typescript
// 批量改：等价于分别赋值，但只触发一次更新
counterStore.$patch({ count: 10, name: 'new' })

// 监听变化（可用于自动持久化）
counterStore.$subscribe((mutation, state) => {
  localStorage.setItem('counter', JSON.stringify(state))
})
```

> 项目 `app.ts` 没用这些，手动在 action 里 `setItem`。状态多了用 `$subscribe` 统一持久化会更省事。

## 3. 项目实战：app.ts 逐行讲解

来看项目里真实存在的 Store：`mall-admin-frontend/src/stores/app.ts`，管理侧边栏折叠状态。

```typescript
// mall-admin-frontend/src/stores/app.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 应用全局状态
 * - 侧边栏折叠状态，持久化到 localStorage
 */
export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('sidebarCollapsed', String(sidebarCollapsed.value))
  }

  return { sidebarCollapsed, toggleSidebar }
})
```

逐行拆解：

- **`defineStore('app', () => {...})`**：定义一个 Store，第一个参数 `'app'` 是唯一 ID（类似后端 Bean 名）。第二个参数是 setup 函数。
- **`ref(localStorage.getItem('sidebarCollapsed') === 'true')`**：State。初始化时从 `localStorage` 读值，把字符串 `'true'` 转成布尔。这是「刷新后恢复状态」的关键——初始化时就从存储里捞。
- **`function toggleSidebar()`**：Action。两件事一起做：翻转状态 + 写回 `localStorage`。注意 `sidebarCollapsed.value` 的 `.value`——`ref` 在 `<script>` 里要带 `.value`，在 `<template>` 里不用。
- **`return { sidebarCollapsed, toggleSidebar }`**：把状态和方法暴露出去。不 return 的变量外部访问不到。

### 组件里怎么用：Navbar.vue

```vue
<!-- mall-admin-frontend/src/layouts/components/Navbar.vue -->
<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import { Fold, Expand } from '@element-plus/icons-vue'

const appStore = useAppStore()   // 拿到 store 实例
</script>

<template>
  <div class="navbar">
    <div class="navbar-left">
      <!-- 点击按钮调 action -->
      <el-icon class="collapse-btn" @click="appStore.toggleSidebar()">
        <!-- 直接读 state，无需 .value -->
        <Fold v-if="!appStore.sidebarCollapsed" />
        <Expand v-else />
      </el-icon>
    </div>
  </div>
</template>
```

`Sidebar.vue` 里同样 `useAppStore()`，根据 `appStore.sidebarCollapsed` 决定菜单宽度。**两个组件共享同一份状态**，点 Navbar 的按钮，Sidebar 立刻响应——这就是状态管理的价值。

## 4. 持久化

### 4.1 为什么需要持久化

Pinia 的 State 存在内存里，**页面一刷新就没了**。用户把侧边栏折叠了，F5 一刷又展开，体验很差。

```
刷新前：sidebarCollapsed = true  (内存)
   │
   │  F5 刷新
   ▼
刷新后：sidebarCollapsed = ???  (内存被清空)
```

持久化的思路：把状态同步写到浏览器的 `localStorage`，下次初始化时再读回来。

### 4.2 localStorage 手动持久化（项目做法）

项目 `app.ts` 用的是最朴素的手动方式：

```typescript
// 初始化：从 localStorage 读
const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')

// 修改时：写回 localStorage
function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem('sidebarCollapsed', String(sidebarCollapsed.value))
}
```

特点：

- **简单直接**，一眼能看懂，没有额外依赖
- 每次改状态都要**手动**写一行 `setItem`，容易漏
- 适合状态少、改动点少的场景（比如本项目就一个折叠状态）

### 4.3 对比 pinia-persistedstate 插件

社区有现成的插件 [`pinia-plugin-persistedstate`](https://prazdevs.github.io/pinia-plugin-persistedstate/)，声明式自动持久化：

```typescript
// 用插件：配置一下就自动同步
export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }
  return { sidebarCollapsed, toggleSidebar }
}, {
  persist: true,   // 自动持久化整个 state，改动自动同步
})
```

| | 手动 localStorage（项目做法） | pinia-persistedstate 插件 |
|------|------|------|
| 依赖 | 无 | 需装插件 |
| 写法 | 每次 `setItem` | 配置 `persist: true` |
| 漏写风险 | 有（忘写就不持久化） | 无 |
| 可控性 | 高（精确控制存什么、何时存） | 中（配置项控制） |
| 适合 | 状态少、简单项目 | 状态多、想省事 |

**本项目为什么用手动？** 学习项目优先暴露原理，让你看清「读 → 改 → 存」的完整链路。生产项目状态多时，用插件更省心。

## 5. 使用场景判断（重要）

这一节最实用。新手最容易犯的错：**把所有数据都塞 Store**。下面给判断标准。

### 5.1 场景速查表

| 场景 | 放 Store？ | 持久化？ | 说明 |
|------|------|------|------|
| 全局 UI 状态（侧边栏折叠、主题、语言） | ✅ 放 | ✅ 持久化 | 跨组件共享，刷新要恢复 |
| 用户登录态（token、用户信息、权限） | ✅ 放 | ✅ 持久化 | 全局共享，刷新不能掉登录 |
| 业务列表数据（商品列表、订单列表） | ❌ 不放 | — | 接口拿一次渲染即可，放 Store 反而要管过期 |
| 业务详情数据（订单详情、商品详情） | ❌ 不放 | — | 只在详情页用，局部状态足够 |
| 表单临时输入 | ❌ 不放 | — | 组件局部 `ref`，提交后即弃 |
| 弹窗开关（只一个组件用） | ❌ 不放 | — | 局部 `ref` 即可 |

### 5.2 各场景详解

**① 全局 UI 状态 → 放 Store + 持久化**

像侧边栏折叠、深色/浅色主题、语言切换，这类状态：

- 多个组件要读（Navbar 改、Sidebar 读、主体区域也要读）
- 用户设过一次，刷新应保持

→ 典型如项目 `app.ts`，放 Store + localStorage 持久化。

**② 业务数据（商品列表、订单详情）→ 不放 Store**

后端同学容易把「列表数据」也塞 Store，这是反模式。原因：

- 列表数据**会过期**，Store 不会自动刷新，容易展示旧数据
- 列表数据**生命周期短**，离开页面就该丢弃
- 放 Store 还要额外管理「何时重新拉取」，徒增复杂度

正确做法：在页面组件里用 `ref` + `onMounted` 调接口拿数据，渲染完事。

```typescript
// ✅ 业务数据：组件局部状态 + 接口直接拿
const productList = ref([])
onMounted(async () => {
  productList.value = await fetchProductList()
})
```

**③ 用户登录态 → 放 Store + 持久化**

token、用户信息、权限列表这类：

- 全局共享（路由守卫要读、请求拦截器要读、UI 要根据权限显隐）
- 刷新不能掉登录

→ 放 Store + 持久化（token 存 localStorage，Store 初始化时读回）。本项目后续 `useUserStore` 会走这条路。

**④ 表单临时状态 → 不放 Store**

表单里输入的「用户名」「手机号」，提交后就丢弃，不该污染全局状态。用组件局部 `ref` 即可。需要跨步骤共享的复杂表单，再考虑 Store。

### 5.3 常见误区

**误区一：把接口返回的列表塞进 Store「方便复用」。**
后端同学容易想「下次再进这个页面就不用重新请求了」。但前端数据要考虑新鲜度——用户可能刚在另一个端改了数据，你缓存旧的反而误导。除非有明确的跨页面共享需求（如「购物车数量角标」），否则别缓存业务列表。

**误区二：每个组件都 `useXxxStore()`，哪怕只用一次。**
Store 的意义是「跨组件共享」。如果只有一个组件用，那就没共享需求，直接局部 `ref` 更简单、更内聚。

**误区三：在 Store 里直接发请求并长期缓存。**
可以这么做（action 里 `await api.getXxx()`），但要清楚：Store 不会自动判断数据是否过期。要么配合「失效时间」字段，要么交给组件在合适时机重新拉取。

### 5.4 一句话决策法

> 问自己两个问题：**「别的组件需要读这个数据吗？」** + **「刷新后需要恢复吗？」**
> 两个都否 → 局部 `ref`；有一个是 → 考虑 Store。

### 5.5 多个 Store 怎么组织

项目里 Store 按职责拆分，一个文件一个 Store，放在 `src/stores/` 下：

```
src/stores/
├── app.ts      # 应用 UI 状态（侧边栏、主题）
├── user.ts     # 用户登录态（token、权限）—— 后续补充
└── ...
```

命名约定：文件名小写，导出的 hook 叫 `useXxxStore`（如 `useAppStore`、`useUserStore`）。Store 之间可以互相调用——在 setup 函数里 `const userStore = useUserStore()` 即可，但要小心循环依赖。

> 拆分原则：**按「业务领域」或「关注点」拆**，别按页面拆。比如 `user` 而不是 `loginPage`，因为登录态在登录页之外也要用。

## 6. 小结

- **状态管理解决的是组件间数据共享**，类比后端的 Session / 全局上下文。
- **Pinia 三件套**：State（`ref`）、Getters（`computed`）、Actions（`function`），setup 语法和 `<script setup>` 一致。
- **解构 State/Getter 用 `storeToRefs`**，解构 Action 直接解构。
- **持久化**：手动 `localStorage`（看清原理）或 `pinia-persistedstate` 插件（省心），项目 `app.ts` 用前者。
- **别什么都塞 Store**：全局 UI 状态、登录态放 Store；业务列表/详情、表单临时值用组件局部状态。
