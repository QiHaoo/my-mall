# 前端架构概述

> 本文档定义前端项目的整体架构、技术栈、目录结构、路由设计和 API 层封装，是前端开发的总纲。
> - 基础设施详细配置见 [infrastructure.md](infrastructure.md)
> - 通用组件与 composables 设计见 [components.md](components.md)
> - 编码规范见 [coding-standards.md](coding-standards.md)
> - 视觉与交互规范见 [design-system.md](design-system.md)
> - 各功能域的前端页面设计见对应后端功能域文档的"前端设计"章节

---

## 一、技术栈

| 类别 | 选型 | 版本 | 说明 |
|------|------|------|------|
| 框架 | Vue 3 | 3.5+ | Composition API + `<script setup>` |
| 语言 | TypeScript | 5.7+ | 类型安全，严格模式 |
| 构建工具 | Vite | 6.x | 极速 HMR |
| UI 组件库 | Element Plus | 2.9+ | 全量引入（不使用按需 resolver） |
| 路由 | Vue Router | 4.5+ | 官方路由 |
| 状态管理 | Pinia | 2.3+ | 官方推荐，TypeScript 友好 |
| HTTP 客户端 | Axios | 1.7+ | 拦截器、统一错误处理 |
| CSS 预处理 | Sass | 1.83+ | SCSS 变量 + 主题定制 |
| 代码规范 | ESLint + Prettier | 9.x / 3.x | 统一代码风格 |
| 自动导入 | unplugin-auto-import + unplugin-vue-components | 19.x / 28.x | Vue/Router/Pinia API 自动导入 |
| 包管理器 | pnpm | 9.x | 高效磁盘占用 |
| 设计工具 | Figma（免费版） | - | 云端协作 UI 设计，Dev Mode 标注交付 |

> **不使用 Tailwind CSS**：项目 UI 统一使用 Element Plus + SCSS 变量系统，保持企业级管理后台一致性。

---

## 二、目录结构

```
mall-admin-frontend/
├── src/
│   ├── api/                        # API 请求层（按模块组织）
│   │   ├── product/                # 商品中心 API
│   │   │   ├── category.ts         # 分类管理 API
│   │   │   └── brand.ts            # 品牌管理 API
│   │   └── types/                  # API 响应类型定义（对应后端 VO）
│   │       ├── common.ts           # R<T>, PageVO<T>, PageQuery 通用类型
│   │       └── product.ts          # 商品中心 DTO/VO 类型
│   ├── assets/
│   │   └── styles/                 # 全局样式
│   │       ├── variables.scss      # SCSS 变量（颜色/间距/字号/圆角/阴影）
│   │       ├── reset.scss          # CSS Reset
│   │       ├── element-plus.scss   # Element Plus 主题覆盖
│   │       ├── utilities.scss      # 通用工具类
│   │       └── index.scss          # 全局样式入口
│   ├── components/                 # 全局通用组件
│   │   ├── PageTable/              # 通用分页表格
│   │   └── FormDialog/             # 通用表单弹窗
│   ├── composables/                # 组合式函数
│   │   ├── useTable.ts             # 分页表格状态管理
│   │   └── useDialog.ts            # 弹窗状态管理
│   ├── layouts/                    # 布局组件
│   │   ├── AdminLayout.vue         # 后台管理主布局
│   │   └── components/
│   │       ├── Sidebar.vue         # 侧边栏
│   │       ├── Navbar.vue          # 顶栏
│   │       ├── Breadcrumb.vue      # 面包屑
│   │       └── AppMain.vue         # 内容区
│   ├── router/
│   │   └── index.ts                # 路由配置
│   ├── stores/                     # Pinia 状态管理
│   │   └── app.ts                  # 应用状态（侧边栏折叠）
│   ├── utils/                      # 工具函数
│   │   ├── request.ts              # Axios 实例 + 拦截器
│   │   └── tree.ts                 # 树形数据工具函数
│   ├── views/                      # 页面组件（按模块组织，与路由对应）
│   │   ├── product/
│   │   │   ├── category/           # 分类管理
│   │   │   │   ├── index.vue
│   │   │   │   └── components/
│   │   │   │       ├── CategoryForm.vue
│   │   │   │       └── CategoryNode.vue
│   │   │   └── brand/              # 品牌管理
│   │   │       ├── index.vue
│   │   │       └── components/
│   │   │           ├── BrandForm.vue
│   │   │           └── BrandRelationDialog.vue
│   │   └── error/
│   │       └── 404.vue             # 404 页面
│   ├── App.vue
│   └── main.ts
├── .env.development                # 开发环境变量
├── .env.production                 # 生产环境变量
├── env.d.ts                        # 环境变量 TypeScript 声明
├── vite.config.ts
├── tsconfig.json                   # Project References 入口
├── tsconfig.app.json               # 应用代码 TS 配置
├── tsconfig.node.json              # Node 端 TS 配置（vite.config.ts）
└── package.json
```

> **目录组织原则**：`views/` 按后端模块组织，与 `api/` 模块一一对应；`components/` 只放跨模块通用组件，模块专属组件放 `views/{module}/components/`。

### 计划中（随功能推进新增）

| 目录 | 触发时机 | 说明 |
|------|---------|------|
| `stores/user.ts` | mall-auth 落地后 | 用户登录态、Token、权限列表 |
| `utils/auth.ts` | 鉴权落地后 | Token 管理 |
| `views/login/` | 鉴权落地后 | 登录页 |
| `components/ImageUpload/` | mall-oss 对接后 | OSS Presigned URL 直传组件 |
| `components/TreeSelect/` | 按需 | 树形选择器封装（当前直接用 el-tree-select） |

---

## 三、路由设计

### 3.1 路由结构

路由按模块分组，与后端服务划分对齐。当前路由配置（[router/index.ts](https://github.com/QiHaoo/my-mall/blob/main/mall-admin-frontend/src/router/index.ts)）：

```typescript
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
```

### 3.2 路由守卫

| 守卫 | 当前 | 计划中 |
|------|------|--------|
| `afterEach` 设置 `document.title` | ✅ 已实现 | - |
| `beforeEach` 鉴权（检查 token，未登录跳登录页） | ❌ | mall-auth 落地后 |
| 动态路由（根据权限菜单生成） | ❌ | 鉴权落地后 |

---

## 四、API 层封装

### 4.1 Axios 实例 + 拦截器

```typescript
// src/utils/request.ts
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,  // 开发环境 http://localhost:1000/api
  timeout: 10000
})

// 请求拦截器：附加 Token（鉴权落地后启用，当前为空实现）
request.interceptors.request.use((config) => {
  // const token = useUserStore().token
  // if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截器：统一处理 R<T>
request.interceptors.response.use(
  (response) => {
    const res = response.data as R<unknown>
    if (res.code === 200) {
      return res.data  // 成功直接返回 data，剥离 R<T> 外壳
    }
    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg))
  },
  (error) => {
    if (error.response?.status === 401) {
      // 后续：跳转登录页
    }
    ElNotification.error({ title: '请求错误', message: error.message || '网络异常' })
    return Promise.reject(error)
  }
)

// 封装泛型方法，返回已剥离 R<T> 外壳的 Promise<T>
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> { ... }
export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> { ... }
export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> { ... }
export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> { ... }
```

> 完整实现见 [infrastructure.md](infrastructure.md) "HTTP 请求层"章节。

### 4.2 API 模块组织

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

### 4.3 类型定义

前端类型定义对应后端 VO/DTO，保持字段名一致。

```typescript
// src/api/types/common.ts
export interface R<T> {
  code: number
  msg: string
  data: T
}

// 注意：后端 Jackson 配置 Long→String 序列化，分页字段为 string 类型
export interface PageVO<T> {
  records: T[]
  total: string
  size: string
  current: string
  pages: string
}

export interface PageQuery {
  pageNum?: number
  pageSize?: number
}
```

```typescript
// src/api/types/product.ts（分类部分）
export interface CategoryVO {
  catId: string          // 雪花 ID，用 string 接收避免精度丢失
  name: string
  parentCid: string
  catLevel: number
  showStatus: number
  sort: number
  icon?: string
  productUnit?: string
  productCount?: number
  children?: CategoryVO[]
}
```

> `R<T>` 和 `PageVO<T>` 与后端 `com.mymall.common.result.R` / `PageVO` 字段一一对应。

---

## 五、状态管理

| Store | 职责 | 持久化 | 状态 |
|-------|------|--------|------|
| `useAppStore` | 侧边栏折叠 | localStorage | ✅ 已实现 |
| `useUserStore` | Token、用户信息、权限列表 | Token 持久化 | ❌ 计划中（mall-auth 落地后） |
| `useTagsViewStore` | 多页签 | - | ❌ 计划中（页面增多后） |

> 业务数据（如商品列表、订单详情）不进 Store，通过 API 直接获取。Store 只放跨页面共享的全局状态。

---

## 六、与后端的协作约定

| 约定 | 说明 |
|------|------|
| API 响应格式 | 统一 `R<T>`，前端响应拦截器自动剥离外壳返回 `data` |
| 分页响应 | 统一 `PageVO<T>`，前端通用 `PageTable` 组件消费；`total` 等字段为 string，使用时 `Number()` 转换 |
| 错误处理 | 后端返回业务错误码 + msg，前端拦截器统一 `ElMessage.error(msg)`；网络错误弹 `ElNotification` |
| 文件上传 | 走 OSS Presigned URL 前端直传，不走网关（计划中，mall-oss 对接后） |
| ID 类型 | 后端雪花 ID 为 Long，Jackson 配置 Long→String 序列化，前端所有 ID 字段用 `string` 接收，避免 19 位精度丢失 |
| 日期格式 | 后端返回 ISO 8601 字符串，前端格式化（计划中引入 dayjs） |
| 网关路由 | 前端 baseURL `/api`，网关 StripPrefix=1 去掉 `/api` 前缀转发到后端服务 |
| CORS | 网关已配置允许 `http://localhost:5173~5176`，开发环境无需 Vite proxy |
