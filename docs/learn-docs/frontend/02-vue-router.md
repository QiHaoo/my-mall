# Vue Router 路由基础

## 1. 什么是前端路由

### 1.1 先看后端路由

做过后端开发的同学一定熟悉 Spring MVC 的 `@RequestMapping`：

```java
@RestController
@RequestMapping("/product")
public class ProductController {

    @GetMapping("/category")
    public R category() { ... }  // 浏览器访问 /product/category → 后端返回整个 HTML 页面
}
```

这种模式下，**每一次 URL 变化都会触发一次完整的 HTTP 请求**，服务器拼接好整个 HTML 页面返回给浏览器，浏览器整体刷新。这就是后端路由（多页应用，MPA）。

```
浏览器                    服务器
  │  GET /product/category  │
  │ ──────────────────────► │
  │                         │  查数据库 + 拼模板
  │  返回完整 HTML          │
  │ ◄────────────────────── │
  │  页面整体刷新 ❌        │
```

### 1.2 前端路由与 SPA

前端路由的核心思想：**URL 变了，但不重新请求页面，而是由 JS 在本地切换显示的组件**。

这种应用叫 SPA（Single Page Application，单页应用）。整个应用只加载一个 `index.html`，之后所有的"页面跳转"都只是：

1. JS 监听 URL 变化
2. 根据路由表找到对应的组件
3. 把旧组件从 `<router-view>` 里卸载，把新组件挂上去

```
浏览器                    服务器
  │  GET / （首次）         │
  │ ──────────────────────► │
  │  返回 index.html + JS   │
  │ ◄────────────────────── │
  │                         │
  │  之后点击 /product/...   │
  │  不再请求服务器 ✅       │
  │  本地切换组件即可        │
```

> **类比**：后端路由是"换 URL = 换一个完整的 HTML 文档"；前端路由是"换 URL = 换一个屏幕上的组件"，文档本身没变。

### 1.3 hash 模式 vs history 模式

前端路由要监听 URL 变化，但又不希望浏览器真的去请求服务器。有两种实现方式：

| | hash 模式 | history 模式 |
|------|------|------|
| URL 形态 | `http://xxx/#/product/category` | `http://xxx/product/category` |
| 原理 | 改 `#` 后面的部分不会触发请求 | 调用 `history.pushState` API 改 URL |
| 服务器配置 | 不需要 | 需要（否则刷新 404） |
| 美观 | 有个 `#`，丑 | 干净，像传统 URL |

**history 模式为什么需要服务器配置？**

因为刷新时浏览器会真的拿 `/product/category` 去请求服务器，而服务器上根本没有这个文件。所以要在 Nginx / 网关上加一条回退规则：所有找不到的路径都返回 `index.html`，交给前端路由处理。

```nginx
location / {
  try_files $uri $uri/ /index.html;
}
```

本项目使用 history 模式（更美观，符合生产标准），对应代码：

```ts
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),  // 使用 history 模式
  routes
})
```

## 2. 路由配置

### 2.1 路由表定义

路由表就是一个数组，每个元素描述"一个 URL 对应哪个组件"：

```ts
import { type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/product/category',           // URL 路径
    name: 'ProductCategory',             // 路由名（编程式导航用）
    component: () => import('@/views/product/category/index.vue'),  // 对应组件
    meta: { title: '分类管理', icon: 'Grid' }                        // 附加信息
  }
]
```

> **类比后端**：这就像是把 `@RequestMapping` 的路径与对应的 Controller 建立映射关系，只不过这里映射的是"前端组件"而不是"后端方法"。

### 2.2 路由嵌套（children）

实际项目里路径往往有层级关系，比如 `/product/category`、`/product/brand` 都在 product 下。Vue Router 用 `children` 描述这种父子结构：

```ts
{
  path: '/',
  component: AdminLayout,         // 父组件（通常是布局容器）
  children: [
    { path: 'product/category', component: ... },  // 实际访问 /product/category
    { path: 'product/brand',    component: ... }   // 实际访问 /product/brand
  ]
}
```

> **注意一个坑**：子路由的 `path` 如果不以 `/` 开头，会自动拼接到父路由后面。父 `path: '/'` + 子 `path: 'product/category'` = `/product/category`。如果子路径写成 `/product/category`（带前导斜杠），就当作绝对路径，不再拼接。

> **类比后端**：类似 Spring MVC 中类上的 `@RequestMapping("/product")` 与方法上的 `@GetMapping("/category")` 拼接成 `/product/category`。

### 2.3 路由懒加载

注意上面组件的写法：

```ts
// ✅ 懒加载：函数形式，用到时才加载
component: () => import('@/views/product/category/index.vue')

// ❌ 直接导入：打包进主包，首次加载就全部下载
import Category from '@/views/product/category/index.vue'
component: Category
```

**为什么需要懒加载？**

如果不做懒加载，所有页面的代码会打包成一个巨大的 `app.js`，用户首次访问要下载几 MB，白屏时间长。懒加载后，每个页面被打成独立的小 chunk，访问到才下载：

```
首次访问 /        → 下载 app.js (200KB) + AdminLayout chunk
点击 /product/category → 才下载 category chunk (50KB)
点击 /product/brand    → 才下载 brand chunk (40KB)
```

**原理**：`() => import('...')` 是 ES 模块的动态导入语法，Webpack / Vite 识别到后会自动代码分割（Code Splitting）。

### 2.4 meta 元信息

`meta` 字段可以挂载任意自定义信息，常见的有：

| 字段 | 用途 |
|------|------|
| `title` | 页面标题（在守卫里设置 `document.title`） |
| `icon` | 菜单图标 |
| `requiresAuth` | 是否需要登录 |

```ts
meta: { title: '分类管理', icon: 'Grid', requiresAuth: true }
```

在导航守卫里可以通过 `to.meta.requiresAuth` 判断是否需要鉴权（后面讲）。

## 3. 导航

### 3.1 声明式导航：`<router-link>`

在模板里用 `<router-link>` 渲染一个链接（默认渲染成 `<a>` 标签），点击后切换路由：

```vue
<template>
  <!-- 等价于 <a href="/product/category">，但不会触发页面刷新 -->
  <router-link to="/product/category">分类管理</router-link>
  <router-link to="/product/brand">品牌管理</router-link>
</template>
```

> **类比**：就像 JSP / Thymeleaf 里写 `<a href="/product/category">`，但区别是点击后不刷新整页，只切换组件。

### 3.2 编程式导航：`router.push()`

在 JS 里主动跳转，常用于"登录成功后跳首页"、"提交表单后跳列表页"：

```ts
import { useRouter } from 'vue-router'

const router = useRouter()

function goCategory() {
  router.push('/product/category')          // 字符串形式
}

function goCategoryWithQuery() {
  router.push({ path: '/product/category', query: { keyword: '手机' } })
  // 结果：/product/category?keyword=手机
}
```

> **类比后端**：类似 `response.sendRedirect("/product/category")`，但前端跳转不发请求。

### 3.3 路由参数：params vs query

| | params | query |
|------|------|------|
| URL 形态 | `/product/category/123` | `/product/category?keyword=手机` |
| 定义方式 | 路径里用占位符 `:id` | 直接拼在 URL 后 |
| 读取方式 | `route.params.id` | `route.query.keyword` |

```ts
// 路由定义
{ path: '/product/category/:id', component: ... }

// 跳转
router.push('/product/category/123')

// 读取
import { useRoute } from 'vue-router'
const route = useRoute()
console.log(route.params.id)  // '123'
```

> **类比后端**：`params` 对应 `@PathVariable`，`query` 对应 `@RequestParam`。

## 4. 导航守卫

### 4.1 什么是导航守卫

导航守卫（Navigation Guards）就是**路由跳转过程中的钩子函数**，可以在跳转前/后做权限校验、日志记录等。

> **类比后端**：导航守卫就是前端的「拦截器 / 过滤器」。Spring 里的 `HandlerInterceptor` 和 Vue Router 的 `beforeEach` 角色几乎一样。

### 4.2 全局守卫

```ts
const router = createRouter({ ... })

// 前置守卫：跳转前执行，常用于鉴权
router.beforeEach((to, from, next) => {
  // to：目标路由  from：来源路由  next：放行/拦截
  if (to.meta.requiresAuth && !isLogin()) {
    next('/login')           // 拦截，跳到登录页
  } else {
    next()                   // 放行
  }
})

// 后置守卫：跳转完成后执行，常用于收尾工作
router.afterEach((to) => {
  document.title = to.meta.title  // 设置页面标题
})
```

`beforeEach` 里的 `next()` 必须调用，否则路由会一直挂起：
- `next()`：放行
- `next('/login')`：拦截并重定向
- `next(false)`：取消本次导航，停在原地

### 4.3 鉴权场景示例

一个典型的后台鉴权流程：

```ts
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth && !token) {
    // 需要登录但没 token → 去登录页
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && token) {
    // 已登录还想去登录页 → 直接回首页
    next('/')
  } else {
    next()
  }
})
```

> **类比后端**：和 Spring Security 的过滤器链一样——请求进来先过一道鉴权过滤器，没权限就重定向到登录页。区别是这里校验的是前端路由切换，不是 HTTP 请求。

### 4.4 守卫的执行顺序

```
导航触发
  └─► beforeEach（全局前置）     ← 鉴权
        └─► 组件内 beforeRouteEnter
              └─► afterEach（全局后置）  ← 改标题、埋点
```

## 5. 布局路由模式

这是后台管理系统最常见的路由组织方式，也是本项目采用的模式。

### 5.1 思路

把"页面骨架"抽成一个布局组件（如 `AdminLayout.vue`），它内部有一个 `<router-view>` 占位。父路由挂这个布局组件，所有业务页面作为子路由，渲染到占位里：

```
AdminLayout.vue
┌─────────────────────────────────────┐
│  顶栏（Logo / 用户菜单）              │
├──────────┬──────────────────────────┤
│ 侧边菜单  │                          │
│ - 分类    │   <router-view />        │ ← 子路由组件渲染在这里
│ - 品牌    │   （Category / Brand）   │
└──────────┴──────────────────────────┘
```

布局组件大致长这样：

```vue
<!-- AdminLayout.vue 简化结构 -->
<template>
  <el-container>
    <el-aside><SideMenu /></el-aside>   <!-- 侧边菜单：固定不变 -->
    <el-container>
      <el-header><TopBar /></el-header>  <!-- 顶栏：固定不变 -->
      <el-main>
        <router-view />                  <!-- 关键：子路由在这里渲染 -->
      </el-main>
    </el-container>
  </el-container>
</template>
```

### 5.2 好处

- 切换页面时，侧边栏和顶栏不会重新渲染，只有中间内容区切换 → 体验流畅
- 公共布局只写一次，业务页面只关心自己的内容
- 菜单高亮、面包屑等可以基于路由 meta 自动生成

## 6. 逐行讲解项目路由配置

下面是本项目 `mall-admin-frontend/src/router/index.ts` 的完整配置，逐行拆解：

```ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
```
- `createRouter`：创建路由实例的工厂函数
- `createWebHistory`：创建 history 模式的 history 对象
- `RouteRecordRaw`：路由记录的类型（TypeScript 类型约束）
- `AdminLayout` 直接 `import`（不是懒加载），因为它是布局骨架，几乎所有页面都要用，没必要拆 chunk

```ts
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: AdminLayout,
    redirect: '/product/category',
    children: [...]
  },
```
- `path: '/'`：根路径
- `component: AdminLayout`：访问根路径时渲染布局组件
- `redirect: '/product/category'`：访问 `/` 时自动重定向到分类管理页（避免空白页）
- `children`：所有业务页面都作为它的子路由，渲染在 `AdminLayout` 的 `<router-view>` 里

```ts
    children: [
      {
        path: 'product/category',
        name: 'ProductCategory',
        component: () => import('@/views/product/category/index.vue'),
        meta: { title: '分类管理', icon: 'Grid' }
      },
```
- `path: 'product/category'`（不带前导 `/`）→ 拼接成 `/product/category`
- `name: 'ProductCategory'`：可以用 `router.push({ name: 'ProductCategory' })` 跳转，比写路径更健壮（路径改了不用全局搜索替换）
- `component: () => import(...)`：懒加载，访问到才下载
- `meta.title`：`afterEach` 里用来设置浏览器标签页标题
- `meta.icon`：侧边菜单渲染图标时读取

```ts
      {
        path: 'product/brand',
        name: 'ProductBrand',
        component: () => import('@/views/product/brand/index.vue'),
        meta: { title: '品牌管理', icon: 'PriceTag' }
      }
    ]
  },
```
- 品牌管理，结构与分类管理完全一致

```ts
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' }
  }
]
```
- `/:pathMatch(.*)*`：通配符路由，匹配任何上面没匹配到的路径
- **必须放在路由表最后**，否则会拦截所有路由（Vue Router 4 按顺序匹配）
- 效果：用户随便输一个不存在的 URL，显示友好的 404 页面而不是白屏

```ts
const router = createRouter({
  history: createWebHistory(),
  routes
})
```
- 创建路由实例，使用 history 模式，挂载上面定义的 routes

```ts
// afterEach：设置页面标题
router.afterEach((to) => {
  document.title = `${to.meta.title || '管理后台'} - 商城管理`
})
```
- 后置守卫：每次路由切换完成后执行
- 把目标路由 `meta.title` 拼到浏览器标题里，比如切换到分类管理页，标题变成 `分类管理 - 商城管理`
- `to.meta.title || '管理后台'`：没有 title 时用默认值兜底

```ts
export default router
```
- 导出路由实例，在 `main.ts` 里 `app.use(router)` 挂载到应用

## 7. 完整工作流程串一遍

以"用户在浏览器输入 `/product/brand` 并回车"为例：

```
1. 浏览器加载 index.html + app.js
2. Vue 应用启动，router 挂载
3. router 监听到 URL 是 /product/brand
4. 遍历 routes 数组匹配 → 命中 children 里的 product/brand
5. 触发 beforeEach（本项目暂未配置，直接放行）
6. 渲染父组件 AdminLayout → 内部 <router-view> 渲染 Brand 组件
7. 触发 afterEach → document.title = '品牌管理 - 商城管理'
8. 页面显示：顶栏 + 侧边栏 + 品牌管理内容
```

之后再点击侧边栏的"分类管理"，`<router-link>` 触发路由切换，**只有 `<router-view>` 里的组件从 Brand 换成 Category**，侧边栏和顶栏纹丝不动——这就是 SPA 的流畅体验来源。

## 8. 小结

| 概念 | 后端类比 | 作用 |
|------|------|------|
| 前端路由 | `@RequestMapping` | URL ↔ 组件映射 |
| 路由表 routes | Controller 映射表 | 集中声明所有路由 |
| children | 类/方法上的 `@RequestMapping` 拼接 | 描述层级关系 |
| 懒加载 `() => import()` | 无 | 按需下载，减小首屏体积 |
| meta | 注解属性 | 挂载标题/图标/权限标记 |
| `<router-link>` | `<a href>` | 声明式导航 |
| `router.push()` | `sendRedirect` | 编程式导航 |
| params / query | `@PathVariable` / `@RequestParam` | 路由参数 |
| beforeEach | `HandlerInterceptor` | 前置鉴权 |
| afterEach | 后置拦截器 / AOP after | 收尾（改标题、埋点） |
| 布局路由 | 模板继承 / layout | 公共骨架 + 内容区切换 |

理解了这些概念，再看本项目的 `router/index.ts` 就不会有陌生感了。后续随着项目演进，会逐步加入鉴权守卫、动态路由（根据用户权限生成菜单）等更高级的用法，但核心都是这套基础。
