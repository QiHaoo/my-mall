# 前端基础设施设计

> 本文档描述 mall-admin-frontend 前端整体框架搭建与基础设施的技术方案，与业务功能解耦。
> 所有配置均与实际代码对齐，"计划中"项随功能推进落地。
> - 架构总纲见 [overview.md](overview.md)
> - 通用组件与 composables 设计见 [components.md](components.md)

---

## 一、工程化基础

### 1.1 Vite 配置

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import path from 'path'

export default defineConfig({
  plugins: [
    vue(),
    // 自动导入 Vue / Vue Router / Pinia 的 API（ref, reactive, useRouter 等）
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts'
    }),
    // 注意：Element Plus 采用全量引入（main.ts 中 app.use(ElementPlus)），
    // 不使用按需引入 resolver，避免样式冲突
    Components({
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') }
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 全局注入 SCSS 变量，所有组件可直接使用 $color-primary 等
        additionalData: `@use "@/assets/styles/variables.scss" as *;`
      }
    }
  },
  server: {
    port: 5173, // 与网关 CORS 配置一致（allowed-origins: http://localhost:5173~5176）
    open: true
  }
})
```

**关键决策**：

| 决策点 | 选择 | 理由 |
|--------|------|------|
| Element Plus 引入方式 | 全量引入（`app.use(ElementPlus)`） | 按需引入与全量引入混用会导致 CSS 加载顺序错乱，全量引入更稳定；打包体积优化后续可单独处理 |
| 自动导入 | `unplugin-auto-import`（API）+ `unplugin-vue-components`（组件） | 免去手动 import ref/reactive/useRouter 等高频 API，提升开发效率 |
| SCSS 全局注入 | `additionalData: @use "variables.scss" as *` | 所有组件可直接用 `$color-primary` 等变量，无需逐个 @use |
| 路径别名 | `@` → `src` | 与 Vue 生态惯例一致 |

> `src/auto-imports.d.ts` 和 `src/components.d.ts` 为构建时自动生成的类型声明文件，已加入 `.gitignore`。

### 1.2 环境变量

| 文件 | 变量 | 值 | 说明 |
|------|------|---|------|
| `.env.development` | `VITE_API_BASE_URL` | `http://localhost:1000/api` | 开发环境走网关 |
| `.env.production` | `VITE_API_BASE_URL` | `/api` | 生产环境同源部署 |

环境变量的 TypeScript 声明在 `env.d.ts`：

```typescript
/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
```

> 网关已配置 CORS 允许 `http://localhost:5173~5176`，开发环境无需 Vite proxy。前端 baseURL 的 `/api` 前缀由网关 `StripPrefix=1` 去除后转发到后端服务。

### 1.3 TypeScript 配置

采用 **Project References** 模式，三文件分工：

| 文件 | 作用 |
|------|------|
| `tsconfig.json` | 入口，仅 `references` 指向 app 和 node |
| `tsconfig.app.json` | 应用代码配置，继承 `@vue/tsconfig/tsconfig.dom.json`，配置 `@/*` 路径别名，`types: ["vite/client"]` |
| `tsconfig.node.json` | Node 端配置（仅 `vite.config.ts`），`target: ES2022`，`strict: true`，`noUnusedLocals/Parameters: true` |

```json
// tsconfig.json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

```json
// tsconfig.app.json 关键配置
{
  "extends": "@vue/tsconfig/tsconfig.dom.json",
  "compilerOptions": {
    "composite": true,
    "baseUrl": ".",
    "paths": { "@/*": ["src/*"] },
    "types": ["vite/client"]
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue", "src/**/*.d.ts"]
}
```

> 构建命令 `pnpm build` 先执行 `vue-tsc -b`（基于 Project References 增量类型检查），再执行 `vite build`。

### 1.4 代码规范工具链

**Prettier**（`.prettierrc.json`）：

```json
{
  "semi": false,           // 无分号
  "singleQuote": true,     // 单引号
  "tabWidth": 2,           // 2 空格缩进
  "printWidth": 100,       // 行宽 100
  "trailingComma": "none", // 无尾逗号
  "arrowParens": "avoid",  // 箭头函数参数省略括号
  "endOfLine": "auto"
}
```

**ESLint**：`@eslint/js` + `typescript-eslint` + `eslint-plugin-vue`，Vue 3 推荐规则。

**脚本**：

| 命令 | 作用 |
|------|------|
| `pnpm lint` | ESLint 检查并自动修复 |
| `pnpm format` | Prettier 格式化 `src/` 目录 |
| `pnpm build` | `vue-tsc -b` 类型检查 + `vite build` 构建 |

> **计划中**：引入 `lint-staged` + `husky` 实现提交前自动修复。当前需手动执行 `pnpm lint`。

### 1.5 包管理器

使用 **pnpm 9.x**。

> **Windows 踩坑**：pnpm 默认全局 store 在 Windows 下可能遇到 EPERM 权限问题。解决方案是使用项目内 store：`pnpm install --store-dir .pnpm-store`，或在 `.npmrc` 中配置 `store-dir=.pnpm-store`。

> **Node 版本要求**：Node 18.20.8+（Vite 6 / unplugin 要求）。

---

## 二、UI 与样式系统

### 2.1 SCSS 变量定义

```scss
// src/assets/styles/variables.scss

// ===== 颜色 =====
$color-primary: #409eff;
$color-success: #67c23a;
$color-warning: #e6a23c;
$color-danger: #f56c6c;
$color-info: #909399;

// ===== 文字颜色 =====
$text-primary: #303133;
$text-regular: #606266;
$text-secondary: #909399;
$text-placeholder: #c0c4cc;

// ===== 边框颜色 =====
$border-base: #dcdfe6;
$border-light: #e4e7ed;
$border-lighter: #ebeef5;

// ===== 背景色 =====
$bg-base: #ffffff;
$bg-page: #f5f7fa;       // 页面背景
$bg-hover: #f5f7fa;

// ===== 侧边栏 =====
$sidebar-bg: #304156;
$sidebar-text: #bfcbd9;
$sidebar-active-text: #409eff;
$sidebar-width: 210px;
$sidebar-collapsed-width: 64px;

// ===== 间距（4px 网格）=====
$spacing-xs: 4px;
$spacing-sm: 8px;
$spacing-md: 12px;
$spacing-base: 16px;
$spacing-lg: 24px;

// ===== 字号 =====
$font-size-xs: 12px;
$font-size-sm: 13px;
$font-size-base: 14px;
$font-size-lg: 16px;
$font-size-xl: 20px;

// ===== 圆角 =====
$radius-sm: 2px;
$radius-base: 4px;
$radius-lg: 8px;

// ===== 阴影 =====
$shadow-base: 0 2px 4px rgba(0, 0, 0, 0.06);
$shadow-md: 0 4px 12px rgba(0, 0, 0, 0.08);
```

> 这些变量通过 Vite `additionalData` 全局注入，所有组件的 `<style scoped lang="scss">` 中可直接使用，无需 `@use`。

### 2.2 样式文件组织

| 文件 | 职责 |
|------|------|
| `variables.scss` | SCSS 变量定义（颜色/间距/字号/圆角/阴影），通过 Vite 全局注入 |
| `reset.scss` | CSS Reset（margin/padding/box-sizing/字体/背景） |
| `element-plus.scss` | 覆盖 Element Plus CSS 变量（`:root` 级别，运行时生效） |
| `utilities.scss` | 通用工具类（flex 布局/间距/文本截断/页面容器/卡片） |
| `index.scss` | 样式入口，聚合引入上述文件 |

```scss
// src/assets/styles/index.scss
@use 'reset.scss';
@use 'element-plus.scss';
@use 'utilities.scss';
```

在 `main.ts` 中引入：`import '@/assets/styles/index.scss'`。

### 2.3 Element Plus 主题定制

```scss
// src/assets/styles/element-plus.scss
// 覆盖 Element Plus CSS 变量（运行时生效，无需 SCSS 编译）
:root {
  --el-color-primary: #409eff;
  --el-color-success: #67c23a;
  --el-color-warning: #e6a23c;
  --el-color-danger: #f56c6c;
  --el-color-info: #909399;
  --el-border-radius-base: 4px;
}
```

> 生产环境主题色通过 CSS 变量覆盖，不使用运行时动态主题（避免 FOUC 闪烁）。

### 2.4 通用工具类

```scss
// src/assets/styles/utilities.scss
// Flex 布局
.flex { display: flex; }
.flex-center { display: flex; align-items: center; justify-content: center; }
.flex-between { display: flex; align-items: center; justify-content: space-between; }
.flex-col { display: flex; flex-direction: column; }
// 间距
.mt-8 { margin-top: 8px; } .mt-16 { margin-top: 16px; } .mt-24 { margin-top: 24px; }
.mb-8 { margin-bottom: 8px; } .mb-16 { margin-bottom: 16px; }
.ml-8 { margin-left: 8px; } .ml-16 { margin-left: 16px; }
// 文本
.text-ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.text-center { text-align: center; }
// 页面容器
.app-container { padding: 24px; }
.card-box { background: #fff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.06); }
```

### 2.5 当前 vs 后续

| 能力 | 当前 | 后续 |
|------|------|------|
| SCSS 变量系统 | ✅ | - |
| Element Plus 主题定制 | ✅ | - |
| 通用工具类 | ✅ | - |
| 暗黑模式 | ❌ | 用户偏好需求 |
| 主题切换（多品牌色） | ❌ | 多租户需求 |

---

## 三、路由系统

### 3.1 路由配置

```typescript
// src/router/index.ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: AdminLayout,
    redirect: '/product/category',
    children: [
      {
        path: 'product/category',
        name: 'ProductCategory',
        component: () => import('@/views/product/category/index.vue'),
        meta: { title: '分类管理', icon: 'Grid' }
      },
      {
        path: 'product/brand',
        name: 'ProductBrand',
        component: () => import('@/views/product/brand/index.vue'),
        meta: { title: '品牌管理', icon: 'PriceTag' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// afterEach：设置页面标题
router.afterEach((to) => {
  document.title = `${to.meta.title || '管理后台'} - 商城管理`
})

export default router
```

**设计要点**：
- 业务路由作为 `AdminLayout` 的 children，复用布局
- 路由懒加载（`() => import()`），按需加载页面
- `meta.title` 用于页面标题和侧边栏菜单文本，`meta.icon` 用于侧边栏图标（Element Plus 图标组件名）
- 404 兜底路由放最后

### 3.2 当前 vs 后续

| 功能 | 当前 | 后续 |
|------|------|------|
| 路由懒加载 | ✅ | - |
| meta（title/icon） | ✅ | - |
| afterEach 设置标题 | ✅ | - |
| beforeEach 鉴权守卫 | ❌ | mall-auth 落地后 |
| 动态路由（权限菜单） | ❌ | 鉴权落地后 |
| TagsView 多页签 | ❌ | 页面增多后 |

---

## 四、状态管理

### 4.1 app store

```typescript
// src/stores/app.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  // 侧边栏折叠状态，从 localStorage 恢复
  const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('sidebarCollapsed', String(sidebarCollapsed.value))
  }

  return { sidebarCollapsed, toggleSidebar }
})
```

> 采用 Pinia 的 Composition API 风格（`defineStore` + setup 函数），与 Vue 3 Composition API 一致。

### 4.2 当前 vs 后续

| Store | 职责 | 当前 | 后续 |
|-------|------|------|------|
| `useAppStore` | 侧边栏折叠 | ✅ | - |
| `useUserStore` | Token、用户信息、权限列表 | ❌ | mall-auth 落地后 |
| `useTagsViewStore` | 多页签状态 | ❌ | 页面增多后 |

> **原则**：业务数据（分类树、品牌列表）不进 Store，通过 API 直接获取。Store 只放跨页面共享的全局状态。

---

## 五、HTTP 请求层

### 5.1 Axios 实例 + 拦截器

```typescript
// src/utils/request.ts
import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage, ElNotification } from 'element-plus'
import type { R } from '@/api/types/common'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,  // http://localhost:1000/api
  timeout: 10000
})

// 请求拦截器：附加 Token（鉴权落地后启用，当前为空实现）
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // const token = useUserStore().token
    // if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理 R<T>
request.interceptors.response.use(
  (response) => {
    const res = response.data as R<unknown>
    if (res.code === 200) {
      return res.data  // 成功直接返回 data，剥离 R<T> 外壳
    }
    // 业务错误：统一弹 Message
    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg))
  },
  (error) => {
    // 网络错误 / 超时 / 401 等
    if (error.response?.status === 401) {
      // 后续：跳转登录页
    }
    ElNotification.error({
      title: '请求错误',
      message: error.message || '网络异常'
    })
    return Promise.reject(error)
  }
)

// 封装泛型方法，返回已剥离 R<T> 外壳的 Promise<T>
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request.get(url, config) as unknown as Promise<T>
}
export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request.post(url, data, config) as unknown as Promise<T>
}
export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request.put(url, data, config) as unknown as Promise<T>
}
export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request.delete(url, config) as unknown as Promise<T>
}

export default request
```

**设计要点**：
- 响应拦截器返回 `res.data`（unknown 类型），泛型方法通过 `as unknown as Promise<T>` 断言，调用方拿到的是已剥离外壳的 `T`
- 业务错误（code !== 200）统一弹 `ElMessage`，网络错误统一弹 `ElNotification`，调用方只需 `try/catch` 处理成功后的逻辑
- 业务方调用 API 函数时不需要再处理 `R<T>` 外壳

### 5.2 类型定义

```typescript
// src/api/types/common.ts

/**
 * 统一响应结构 R<T>
 * 对应后端 com.mymall.common.result.R
 */
export interface R<T> {
  code: number
  msg: string
  data: T
}

/**
 * 分页响应 PageVO<T>
 * 对应后端 com.mymall.common.result.PageVO
 *
 * 注意：后端 Jackson 配置了 Long→String 序列化，分页字段（total/current/size/pages）
 * 也为 string 类型。前端使用时需用 Number() 转换。
 */
export interface PageVO<T> {
  records: T[]
  total: string
  size: string
  current: string
  pages: string
}

/**
 * 分页查询基础参数
 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}
```

> **⚠️ Long→String 精度问题**：后端雪花 ID 为 19 位 Long，JavaScript Number 最大安全整数为 16 位（`Number.MAX_SAFE_INTEGER` = 9007199254740991），直接用 number 接收会精度丢失。后端 Jackson 全局配置了 Long→String 序列化，因此前端所有 ID 字段和分页字段都为 `string` 类型。分页字段使用时需 `Number()` 转换。

### 5.3 API 模块组织

每个后端模块一个目录，每个功能域一个文件，类型定义集中放 `api/types/`：

```typescript
// src/api/product/category.ts
import { get, post, put } from '@/utils/request'
import type { CategoryVO, CategorySaveDTO, CategoryUpdateDTO, CategorySortItem } from '@/api/types/product'

export function getCategoryTree() {
  return get<CategoryVO[]>('/product/category/tree')
}
export function createCategory(data: CategorySaveDTO) {
  return post<void>('/product/category', data)
}
```

### 5.4 当前 vs 后续

| 功能 | 当前 | 后续 |
|------|------|------|
| Axios 实例 + baseURL | ✅ | - |
| 响应拦截器剥离 R\<T\> | ✅ | - |
| 业务错误 ElMessage | ✅ | - |
| 网络错误 ElNotification | ✅ | - |
| 请求拦截器 token 注入 | ❌（空实现） | 鉴权落地后 |
| 401 跳转登录 | ❌ | 鉴权落地后 |
| 请求取消 / 重试 | ❌ | 后续 |
| 请求 loading 全局 | ❌ | 后续 |

---

## 六、后台管理布局

### 6.1 布局结构

```
┌──────────────────────────────────────────────┐
│  Navbar（折叠按钮 + 面包屑）        高 50px   │
├──────────┬───────────────────────────────────┤
│          │                                   │
│ Sidebar  │        AppMain                    │
│ 210px    │   （router-view + transition）    │
│ /64px    │                                   │
│          │                                   │
└──────────┴───────────────────────────────────┘
```

### 6.2 组件职责

| 组件 | 职责 |
|------|------|
| [AdminLayout.vue](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/layouts/AdminLayout.vue) | 布局容器，flex 布局组合 Sidebar + 主容器（Navbar + AppMain） |
| [Sidebar.vue](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/layouts/components/Sidebar.vue) | 侧边栏菜单，深色背景 `#304156`，从路由配置自动生成菜单项，支持折叠 |
| [Navbar.vue](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/layouts/components/Navbar.vue) | 顶栏，折叠按钮（调 app store toggleSidebar）+ Breadcrumb |
| [Breadcrumb.vue](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/layouts/components/Breadcrumb.vue) | 从 `route.matched` 自动生成面包屑 |
| [AppMain.vue](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/layouts/components/AppMain.vue) | 内容区，`<router-view>` + `<transition>` 淡入过渡 |

### 6.3 侧边栏菜单生成

侧边栏菜单从路由配置的 `meta` 自动生成，不硬编码：

```typescript
// 从 router.options.routes 提取 AdminLayout 的 children 作为菜单项
const menuItems = computed(() => {
  const rootRoute = router.options.routes.find((r) => r.path === '/')
  return rootRoute?.children || []
})
```

- 每个菜单项：`{ path, meta: { title, icon } }`
- `icon` 使用 Element Plus 图标组件名（如 `'Grid'`），通过 `:is="iconMap[name]"` 动态渲染
- 当前激活菜单高亮：`:default-active="route.path"`

### 6.4 当前 vs 后续

| 功能 | 当前 | 后续 |
|------|------|------|
| 侧边栏 + 折叠 | ✅ | - |
| 顶栏 + 面包屑 | ✅ | - |
| 内容区 transition | ✅ | - |
| 用户菜单（头像/退出） | ❌ | 鉴权落地后 |
| TagsView 多页签 | ❌ | 页面增多后 |
| 全屏切换 | ❌ | 后续 |

---

## 七、通用组件与 Composables

> 通用组件的完整 API 设计（Props/Slots/Expose/使用示例）见 [components.md](components.md)，本节只做概览。

### 7.1 已实现

| 组件/Composable | 文件 | 职责 |
|----------------|------|------|
| PageTable | [components/PageTable/index.vue](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/components/PageTable/index.vue) | 通用分页表格：搜索栏 + 工具栏插槽 + el-table + el-pagination，内部自管理分页状态 |
| FormDialog | [components/FormDialog/index.vue](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/components/FormDialog/index.vue) | 通用表单弹窗：el-dialog + el-form + 校验 + 提交 loading，泛型支持 |
| useTable | [composables/useTable.ts](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/composables/useTable.ts) | 分页表格状态管理 composable |
| useDialog | [composables/useDialog.ts](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/composables/useDialog.ts) | 弹窗状态管理 composable |

### 7.2 计划中（随功能推进）

| 组件 | 触发时机 | 说明 |
|------|---------|------|
| ImageUpload | mall-oss 鉴权对接后 | OSS Presigned URL 直传封装（品牌 Logo、分类图标） |
| TreeSelect | 按需 | 树形选择器封装（当前直接用 el-tree-select） |
| RichTextEditor | SPU 模块 | 富文本编辑器 |
| SvgIcon | 后续 | 自定义 SVG 图标组件 |

---

## 八、工具函数

### 8.1 已实现

| 工具 | 文件 | 函数 | 说明 |
|------|------|------|------|
| request | [utils/request.ts](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/utils/request.ts) | `get/post/put/del` | Axios 封装，拦截器剥离 R\<T\> |
| tree | [utils/tree.ts](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/utils/tree.ts) | `traverseTree` | 深度优先遍历树，回调返回 false 跳过子节点 |
| tree | 同上 | `findNode` | 按条件查找第一个匹配节点 |
| tree | 同上 | `getNodePath` | 获取从根到目标节点的路径数组 |
| tree | 同上 | `isDescendant` | 判断 target 是否是 ancestor 的子孙（拖拽循环引用检测） |

> `tree.ts` 用于分类管理拖拽排序时计算受影响节点、检测循环引用。

### 8.2 计划中

| 工具 | 触发时机 | 说明 |
|------|---------|------|
| dayjs 封装 | 日期字段出现时 | 日期格式化 |
| 金额格式化 | 订单模块 | 分→元转换 + 千分位 |
| 防抖节流 | 按需 | 搜索输入等场景 |

---

## 九、与后端的协作约定

| 约定 | 说明 |
|------|------|
| API 响应格式 | 统一 `R<T>`，前端响应拦截器自动剥离外壳返回 `data` |
| 分页响应 | 统一 `PageVO<T>`，`total` 等字段为 string，使用时 `Number()` 转换 |
| 错误处理 | 后端返回业务错误码 + msg，前端拦截器统一 `ElMessage.error(msg)`；网络错误弹 `ElNotification` |
| ID 类型 | 后端雪花 ID 为 Long，Jackson 配置 Long→String 序列化，前端所有 ID 字段用 `string` 接收 |
| 网关路由 | 前端 baseURL `/api`，网关 StripPrefix=1 去掉 `/api` 前缀转发到后端服务 |
| CORS | 网关已配置允许 `http://localhost:5173~5176`，开发环境无需 Vite proxy |
| 文件上传 | 走 OSS Presigned URL 前端直传，不走网关（计划中，mall-oss 对接后） |
| 日期格式 | 后端返回 ISO 8601 字符串，前端格式化（计划中引入 dayjs） |

---

## 十、踩坑记录

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| Element Plus 样式错乱 | 按需引入与全量引入混用导致 CSS 加载顺序冲突 | 统一全量引入（`app.use(ElementPlus)`），不使用 `ElementPlusResolver` |
| pnpm Windows EPERM | pnpm 全局 store 权限问题 | 使用项目内 store：`--store-dir .pnpm-store` |
| 雪花 ID 精度丢失 | JS Number 最大安全整数 16 位，雪花 ID 19 位 | 后端 Jackson 配置 Long→String 序列化，前端 ID 字段用 string |
| 品牌新增后列表不刷新 | brand/index.vue 同时用了 useTable 和 PageTable 内部状态，状态不一致 | 移除 useTable，通过 PageTable ref 调用 `refresh()` |
