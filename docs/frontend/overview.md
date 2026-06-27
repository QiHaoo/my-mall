# 前端架构概述

> 本文档定义前端项目的整体架构、技术栈、目录结构、路由设计和 API 层封装。
> 各功能域的前端页面设计见对应功能域文档的"前端设计"章节。

---

## 一、技术栈

| 类别 | 选型 | 说明 |
|------|------|------|
| 框架 | Vue 3 | Composition API |
| 语言 | TypeScript | 类型安全 |
| 构建工具 | Vite | 极速 HMR |
| UI 组件库 | Element Plus | Vue 3 生态最成熟的企业级 UI 库 |
| 路由 | Vue Router 4 | 官方路由 |
| 状态管理 | Pinia | 官方推荐，TypeScript 友好 |
| HTTP 客户端 | Axios | 拦截器、取消请求、统一错误处理 |
| 设计工具 | Figma（免费版） | 云端协作 UI 设计，Dev Mode 标注交付 |

---

## 二、目录结构

```
mall-admin-frontend/
├── public/                     # 静态资源
├── src/
│   ├── api/                    # API 请求层（按模块组织）
│   │   ├── product/            # 商品中心 API
│   │   │   ├── category.ts     # 分类管理 API
│   │   │   ├── brand.ts        # 品牌管理 API
│   │   │   └── attr.ts         # 属性管理 API
│   │   ├── member/             # 会员中心 API
│   │   └── types/              # API 响应类型定义（对应后端 VO）
│   ├── assets/                 # 全局样式、图片、字体
│   ├── components/             # 全局通用组件
│   │   ├── PageTable/          # 分页表格封装
│   │   ├── TreeSelect/         # 树形选择器封装
│   │   └── ImageUpload/        # 图片上传组件（对接 OSS Presigned URL）
│   ├── composables/            # 组合式函数（useTable、useDialog 等）
│   ├── layouts/                # 布局组件
│   │   └── AdminLayout.vue     # 后台管理主布局（侧边栏 + 顶栏 + 内容区）
│   ├── router/                 # 路由配置
│   │   └── index.ts
│   ├── stores/                 # Pinia 状态管理
│   │   ├── user.ts             # 用户登录态
│   │   └── app.ts              # 应用全局状态（侧边栏折叠等）
│   ├── utils/                  # 工具函数
│   │   ├── request.ts          # Axios 实例 + 拦截器
│   │   └── auth.ts             # Token 管理
│   ├── views/                  # 页面组件（按模块组织，与路由对应）
│   │   ├── product/
│   │   │   ├── category/
│   │   │   │   └── index.vue
│   │   │   ├── brand/
│   │   │   └── attr/
│   │   └── login/
│   ├── App.vue
│   └── main.ts
├── .env.development            # 开发环境变量
├── .env.production             # 生产环境变量
├── vite.config.ts
└── tsconfig.json
```

> **目录组织原则**：`views/` 按后端模块组织，与 `api/` 的模块一一对应；`components/` 只放跨模块通用的组件，模块专属组件放 `views/{module}/components/`。

---

## 三、路由设计

### 3.1 路由结构

路由按模块分组，与后端服务划分对齐：

```typescript
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('@/views/login/index.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: AdminLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'product/category',
        component: () => import('@/views/product/category/index.vue'),
        meta: { title: '分类管理', icon: 'Grid' }
      },
      {
        path: 'product/brand',
        component: () => import('@/views/product/brand/index.vue'),
        meta: { title: '品牌管理', icon: 'PriceTag' }
      }
      // ...其他模块路由
    ]
  }
]
```

### 3.2 路由守卫

- `beforeEach`：检查 `requiresAuth`，未登录跳转 `/login`
- `afterEach`：设置 `document.title` = `meta.title`

---

## 四、API 层封装

### 4.1 Axios 实例 + 拦截器

```typescript
// src/utils/request.ts
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,  // 网关地址
  timeout: 10000
})

// 请求拦截器：附加 Token
request.interceptors.request.use(config => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截器：统一处理 R<T>
request.interceptors.response.use(
  response => {
    const { code, msg, data } = response.data
    if (code === 200) {
      return data  // 成功直接返回 data，剥离 R<T> 外壳
    }
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  error => {
    // 401 → 跳转登录
    // 网络错误 → 提示
    return Promise.reject(error)
  }
)
```

### 4.2 API 模块组织

每个后端模块一个目录，每个功能域一个文件，类型定义集中放 `api/types/`：

```typescript
// src/api/product/category.ts
import request from '@/utils/request'
import type { CategoryVO, CategoryTreeNode } from '@/api/types/product'

export function getCategoryTree() {
  return request.get<CategoryTreeNode[]>('/product/category/tree')
}

export function createCategory(data: CategoryForm) {
  return request.post<void>('/product/category', data)
}
```

### 4.3 类型定义

前端类型定义对应后端 VO，保持字段名一致：

```typescript
// src/api/types/product.ts
export interface CategoryVO {
  id: number
  name: string
  parentId: number
  sort: number
  icon?: string
  children?: CategoryVO[]
}

export interface PageVO<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}
```

> `PageVO<T>` 与后端 `com.mymall.common.result.PageVO` 字段一一对应。

---

## 五、状态管理

| Store | 职责 | 持久化 |
|-------|------|--------|
| `useUserStore` | Token、用户信息、权限列表 | Token 持久化到 localStorage |
| `useAppStore` | 侧边栏折叠、主题 | 持久化到 localStorage |

> 业务数据（如商品列表、订单详情）不进 Store，通过 API 直接获取。Store 只放跨页面共享的全局状态。

---

## 六、与后端的协作约定

| 约定 | 说明 |
|------|------|
| API 响应格式 | 统一 `R<T>`，前端拦截器自动剥离外壳 |
| 分页响应 | 统一 `PageVO<T>`，前端通用 `PageTable` 组件消费 |
| 错误处理 | 后端返回业务错误码 + msg，前端拦截器统一 `ElMessage.error(msg)` |
| 文件上传 | 走 OSS Presigned URL 前端直传，不走网关 |
| 日期格式 | 后端返回 ISO 8601 字符串，前端 day.js 格式化 |
| ID 类型 | 后端雪花 ID 为 Long，前端用 string 接收避免精度丢失 |
