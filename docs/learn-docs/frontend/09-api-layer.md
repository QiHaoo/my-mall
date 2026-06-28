# API 层设计与 Axios 封装

> 前置阅读：[01-Vue3 基础](./01-vue3-basics.md) ~ [06-Vite 构建](./06-vite-build.md)
>
> 涉及文件：`src/utils/request.ts`、`src/api/types/common.ts`、`src/api/types/product.ts`、`src/api/product/category.ts`、`src/api/product/brand.ts`
>
> 本文对照项目代码逐行讲解前端 API 层的设计与实现，并随时对比后端的 `R<T>`、`PageVO<T>`、Controller/Service 分层，帮助你建立前后端协作的全局视角。

## 1. Axios 基础

### 1.1 什么是 Axios

Axios 是一个基于 Promise 的 HTTP 客户端，浏览器和 Node.js 都能用。前端要调后端接口，本质上就是发 HTTP 请求，Axios 就是干这个的。

```
┌──────────────┐   HTTP 请求    ┌──────────────┐
│  Vue 组件    │ ─────────────► │  后端接口    │
│ (浏览器)     │ ◄───────────── │ (Spring MVC) │
└──────────────┘   JSON 响应    └──────────────┘
        │
        │  axios.get('/product/brand')
        │  axios.post('/product/brand', data)
        ▼
   ┌──────────┐
   │  Axios   │  ← 封装 XMLHttpRequest / fetch
   └──────────┘
```

### 1.2 Axios vs 原生 fetch

浏览器自带 `fetch` 也能发请求，为什么还要引入 Axios？

| 对比项 | 原生 fetch | Axios |
|------|------|------|
| 返回值 | Promise<Response>，需手动 `.json()` | Promise，自动转换 JSON |
| 请求/响应拦截器 | ❌ 没有 | ✅ 内置 |
| 超时控制 | 需手写 AbortController | `timeout: 10000` 一行搞定 |
| 错误处理 | 4xx/5xx 不 reject，要手动判断 | 非 2xx 自动 reject |
| 取消请求 | AbortController（较新） | `CancelToken` / `AbortController` |
| JSON 转换 | 手动 `res.json()` | 自动 `JSON.parse` / `JSON.stringify` |
| TypeScript | 类型支持一般 | 类型支持好 |

一个最直观的例子——发 GET 请求并拿数据：

```typescript
// ❌ fetch：两步走，还要判断 ok
const res = await fetch('/api/product/brand')
if (!res.ok) throw new Error('请求失败')
const data = await res.json()  // 还要再 await 一次

// ✅ Axios：一步到位
const { data } = await axios.get('/product/brand')
```

### 1.3 拦截器：前端的 Filter

Axios 最强大的能力是**拦截器**——在请求发出前、响应返回后插入统一逻辑。

如果你写过 Spring 的 `Filter` 或 `HandlerInterceptor`，这个概念完全一样：

```
后端 Spring MVC 请求链路：
  Request → Filter → DispatcherServlet → Interceptor → Controller
                                                        ↓
  Response ← Filter ← DispatcherServlet ← Interceptor ← Controller

前端 Axios 请求链路：
  调用 axios.get() → 请求拦截器 → 发出 HTTP → 收到响应 → 响应拦截器 → 业务代码
                       (加 Token)                            (剥离 R<T>、统一报错)
```

| 后端组件 | 前端对应 | 作用 |
|------|------|------|
| `Filter`（Servlet 规范） | 请求/响应拦截器 | 在请求处理前后插入通用逻辑 |
| `HandlerInterceptor.preHandle` | 请求拦截器 | 请求发出前执行（如加 Token） |
| `HandlerInterceptor.postHandle` | 响应拦截器（成功分支） | 响应回来后处理 |
| `@RestControllerAdvice` | 响应拦截器（错误分支） | 统一异常兜底 |

### 1.4 为什么选 Axios

项目的选型理由（见 `docs/frontend/overview.md` 技术栈表）：

- **拦截器**：统一加 Token、统一剥离 `R<T>` 外壳、统一报错，业务代码只关心成功数据
- **超时控制**：`timeout: 10000` 一行配置，避免请求挂死
- **取消请求**：后续搜索联想、页面切换取消未完成请求时会用到
- **TypeScript 友好**：泛型支持完善，能配合项目的严格类型检查

## 2. request.ts 封装逐行讲解

> 文件位置：`src/utils/request.ts`

整个文件只做一件事：创建一个配置好拦截器的 Axios 实例，并导出泛型化的 `get/post/put/del` 方法。下面逐段讲解。

### 2.1 创建实例

```typescript
// request.ts 第 1-14 行
import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage, ElNotification } from 'element-plus'
import type { R } from '@/api/types/common'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000
})
```

- `axios.create()` 创建一个独立实例，不污染全局 `axios` 默认配置。多个后端地址时可以创建多个实例。
- `baseURL`：所有请求的前缀，从 Vite 环境变量读取。
  - 开发环境（`.env.development`）：`VITE_API_BASE_URL=http://localhost:1000/api`
  - 生产环境（`.env.production`）：`VITE_API_BASE_URL=/api`（同源部署走相对路径）
- `timeout: 10000`：10 秒超时，超时后请求自动中断并进入错误拦截器。后端接口正常响应应在秒级，10 秒是兜底保护。
- `ElMessage` / `ElNotification`：Element Plus 的两种提示组件，前者用于轻量业务错误提示，后者用于较重的网络错误通知。

> 对比后端：`baseURL` 类似 Feign 的 `@FeignClient(name = "mall-product")` 通过服务名定位地址；`timeout` 类似 Feign 的 `request.options` 超时配置。

### 2.2 请求拦截器

```typescript
// request.ts 第 17-24 行
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // const token = useUserStore().token
    // if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => Promise.reject(error)
)
```

当前是**空实现**，两行核心逻辑被注释了，注释说明「鉴权落地后启用」。这是因为 `mall-auth` 认证服务还没对接，目前没有 Token 可注入。

设计意图：等 `useUserStore` 落地后，解开注释即可，每个请求自动带上 `Authorization: Bearer xxx`。这跟后端网关里的鉴权过滤器是同一个思路——在请求进入业务逻辑前统一处理鉴权。

> 对比后端：相当于 `mall-gateway` 里的全局鉴权 Filter，每个请求到达业务服务前先校验 Token。只不过前端是在「发出前」注入，后端是在「到达后」校验。

### 2.3 响应拦截器（核心）

这是整个封装最关键的部分——**剥离 `R<T>` 外壳**：

```typescript
// request.ts 第 27-37 行
request.interceptors.response.use(
  (response) => {
    const res = response.data as R<unknown>
    if (res.code === 200) {
      // 成功直接返回 data，剥离 R<T> 外壳
      return res.data
    }
    // 业务错误：统一弹 Message
    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg))
  },
  ...
)
```

逐行拆解：

- `response.data`：Axios 自动解析后的响应体，就是后端返回的 JSON 对象。
- `as R<unknown>`：把响应体断言成 `R<T>` 结构。后端所有接口都返回 `{ code, msg, data }`，这里类型上用 `unknown` 表示「data 具体类型由调用方泛型决定」。
- `res.code === 200`：判断业务是否成功。后端约定 `code=200` 表示成功，其他都是业务错误。
- `return res.data`：**成功时只返回 `data` 字段**，把 `code`/`msg` 外壳丢掉。这样业务代码拿到的就是纯数据，不用每次写 `res.data.data`。
- `ElMessage.error(res.msg)`：业务错误统一弹轻提示。`res.msg` 是后端给的提示文案，如「参数错误」「品牌名已存在」。
- `Promise.reject(new Error(res.msg))`：reject 让调用方的 `try/catch` 能捕获到，但不用再自己弹错（拦截器已经弹了）。

### 2.4 错误拦截器

```typescript
// request.ts 第 38-48 行
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
```

这个分支处理的是**没拿到正常响应**的情况（HTTP 层面错误）：

- `error.response?.status === 401`：未授权。当前是 TODO，等 `mall-auth` 落地后这里会跳转登录页并清空 Token。
- `ElNotification.error`：用通知组件而非轻提示，因为网络错误比业务错误更严重，需要更醒目的展示。`error.message` 可能是 `Network Error`、`timeout of 10000ms exceeded` 等。

| 错误类型 | 触发条件 | 处理方式 | 提示组件 |
|------|------|------|------|
| 业务错误 | HTTP 200，但 `R.code !== 200` | `ElMessage.error(msg)` | 轻提示（右上角小弹窗） |
| 网络错误 | 断网、超时、跨域、502 等 | `ElNotification.error` | 通知（右侧大弹窗） |
| 401 未授权 | Token 过期/缺失 | 跳登录页（TODO） | — |

### 2.5 泛型方法封装

```typescript
// request.ts 第 55-69 行
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
```

四个方法对应 HTTP 的 GET/POST/PUT/DELETE，泛型 `T` 表示「成功时返回的数据类型」。

**为什么要 `as unknown as Promise<T>`？** 这是整个封装里最需要理解的一行。

Axios 的类型声明里，`request.get()` 返回 `Promise<AxiosResponse<T>>`。但我们的响应拦截器已经把返回值改成了 `res.data`（一个 `unknown`），Axios 的类型系统并不知道拦截器干了这个事，类型还是老的。

所以这里存在一个**类型断层**：实际运行时返回的是 `T`，但 TypeScript 推断的是 `AxiosResponse`。`as unknown as Promise<T>` 就是手动告诉编译器：「相信我，拦截器已经把数据剥离成 `T` 了」。

```
调用方期望:    Promise<PageVO<BrandVO>>     ← 泛型 T = PageVO<BrandVO>
拦截器实际返回: res.data (unknown)           ← 运行时正确，但类型丢失
Axios 声明:    Promise<AxiosResponse>       ← 类型系统看到的
                                    ↑
                      as unknown as Promise<T> 把类型「修正」回来
```

调用示例，类型安全且无需手动剥壳：

```typescript
// 业务代码直接拿到 BrandVO[]，不用 res.data.data
const page = await getBrandPage(params)  // Promise<PageVO<BrandVO>>
page.records.forEach(b => console.log(b.name))
```

### 2.6 设计决策：为什么返回 res.data 而不是 response

很多人初学会疑惑：拦截器为什么不返回完整的 `response`，而只返回 `res.data`？

**因为后端的统一响应体 `R<T>` 已经在 `response.data` 里了。** 看看后端 `R.java`：

```java
// mall-common: com.mymall.common.result.R
@Data
public class R<T> implements Serializable {
    private Integer code;   // 业务状态码：200=成功
    private String msg;     // 提示消息
    private T data;         // 业务数据
}
```

完整的 HTTP 响应是三层嵌套：

```
AxiosResponse              ← response（含 status/headers/config）
  └── response.data        ← R<T>（后端统一响应体）
        ├── code: 200
        ├── msg: "success"
        └── data: T         ← 真正的业务数据
```

如果不剥壳，业务代码要写 `response.data.data`，每个接口都要重复。拦截器剥到 `res.data` 后，业务代码直接拿 `T`，干净利落。

> 对比后端：这就像后端 `GlobalExceptionHandler` 把异常统一转成 `R` 后，Controller 不用关心异常包装，只管 `return R.ok(data)`。前端拦截器做的是对称的事——把 `R` 拆开，业务代码只管用 `data`。

## 3. 类型定义体系

> 文件位置：`src/api/types/common.ts`、`src/api/types/product.ts`

### 3.1 R<T> 接口

```typescript
// common.ts 第 5-9 行
export interface R<T> {
  code: number
  msg: string
  data: T
}
```

与后端 `com.mymall.common.result.R` 字段一一对应：

| 后端 Java | 前端 TS | 说明 |
|------|------|------|
| `Integer code` | `code: number` | 业务状态码 |
| `String msg` | `msg: string` | 提示消息 |
| `T data` | `data: T` | 泛型业务数据 |

注意：前端 `R<T>` 主要在拦截器内部用（`as R<unknown>`），业务代码基本不直接接触，因为拦截器已经把外壳剥掉了。

### 3.2 PageVO<T> 接口

```typescript
// common.ts 第 18-24 行
export interface PageVO<T> {
  records: T[]
  total: string
  size: string
  current: string
  pages: string
}
```

对应后端 `com.mymall.common.result.PageVO`，后端定义如下：

```java
@Data
public class PageVO<T> implements Serializable {
    private List<T> records;   // 数据列表
    private long total;        // 总记录数
    private long current;      // 当前页码
    private long size;         // 每页数量
    private long pages;        // 总页数
}
```

**重点：后端是 `long`，前端却定义为 `string`。** 这不是写错了，而是有意为之，原因见 3.5 节。

后端 `PageVO` 故意不直接返回 MyBatis-Plus 的 `Page`，是为了隔离 ORM 内部字段（`orders`、`optimizeCountSql` 等），只暴露前端需要的 5 个字段。前端 `PageVO<T>` 精确对应这 5 个字段。

### 3.3 PageQuery 接口

```typescript
// common.ts 第 29-32 行
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}
```

分页查询的基础参数，对应后端 `com.mymall.common.query.PageQuery`。字段都可选（`?`），因为有些查询不带分页。各业务模块的查询 DTO 会扩展它（如 `BrandQueryDTO` 加 `name`、`firstLetter` 等过滤条件）。

### 3.4 业务类型

以品牌为例（`product.ts` 第 59-94 行）：

```typescript
/** 品牌详情/列表项 VO（对应后端 BrandVO） */
export interface BrandVO {
  id: string              // 雪花 ID，string 接收
  name: string
  logo: string
  descript?: string
  showStatus: number
  firstLetter?: string
  sort: number
  version?: number        // 乐观锁版本号
}

/** 新增/修改品牌 DTO（对应后端 BrandSaveDTO） */
export interface BrandSaveDTO {
  id?: string             // 修改时携带
  name: string
  logo: string
  descript?: string
  showStatus?: number
  firstLetter?: string
  sort?: number
  version?: number
}

/** 品牌分页查询条件（对应后端 BrandQueryDTO） */
export interface BrandQueryDTO {
  pageNum?: number
  pageSize?: number
  name?: string
  firstLetter?: string
  showStatus?: number
}
```

命名规则与后端严格对齐：

| 前端 interface | 后端 class | 用途 |
|------|------|------|
| `BrandVO` | `BrandVO` | 响应数据（列表项/详情） |
| `BrandSaveDTO` | `BrandSaveDTO` | 请求参数（新增/修改） |
| `BrandQueryDTO` | `BrandQueryDTO` | 查询条件 |

分类的类型同理（`CategoryVO`、`CategorySaveDTO`、`CategoryUpdateDTO`、`CategorySortItem`），还有品牌关联分类的 `BrandRelationVO`，这里不一一列举。

### 3.5 设计决策：为什么 ID 用 string

后端数据库主键用雪花算法生成，是 19 位的 `Long`，例如 `1402727468465135618`。但 JavaScript 的 `Number` 类型最大安全整数只有 16 位：

```javascript
Number.MAX_SAFE_INTEGER  // 9007199254740991（16 位）
1402727468465135618      // 19 位，超出安全范围
```

如果前端用 `number` 接收，会发生精度丢失：

```javascript
JSON.parse('{"id": 1402727468465135618}')
// { id: 1402727468465135600 }  ← 末尾 3 位变了！
```

**解决方案**：后端 `JacksonConfig` 全局配置了 `Long→String` 序列化，所有 `Long` 字段在 JSON 里变成字符串。前端用 `string` 接收，需要运算时再 `Number()` 转换。

这就是为什么 `PageVO` 的 `total/size/current/pages` 也是 `string`——它们后端是 `long`，同样会被序列化成字符串。分页组件要用时得 `Number(page.total)`。

> 这是前后端协作中**最容易踩的坑之一**。后端开发者往往意识不到 JS 的精度限制，前端用 `number` 接收又会静默出错（不报错，只是末尾几位变 0）。统一用 `string` 是业界标准做法。

### 3.6 类型放 api/types/ 的组织理由

类型定义集中放在 `api/types/`，而不是各模块目录下，原因有三：

1. **跨模块复用**：`R<T>`、`PageVO<T>`、`PageQuery` 是通用的，所有模块都要用。放公共位置避免重复定义。
2. **与后端包结构对齐**：后端公共类型在 `com.mymall.common.result`，业务类型按模块的 `vo`/`dto` 包组织。前端 `api/types/common.ts` 对应公共类型，`api/types/product.ts` 对应商品模块类型，结构上镜像。
3. **导入路径统一**：所有 API 文件都从 `@/api/types/xxx` 导入类型，路径一致，便于查找。

```
api/
├── types/              ← 类型定义（对应后端 VO/DTO）
│   ├── common.ts       ← R<T>、PageVO<T>、PageQuery（对应 common 模块）
│   └── product.ts      ← 商品模块所有 DTO/VO
├── product/            ← 商品模块 API（对应后端 Controller）
│   ├── category.ts
│   └── brand.ts
```

## 4. API 模块组织

### 4.1 目录结构

每个后端模块一个目录，每个功能域一个文件：

```typescript
// src/api/product/category.ts
import { get, post, put } from '@/utils/request'
import type { CategoryVO, CategorySaveDTO, CategoryUpdateDTO, CategorySortItem } from '@/api/types/product'

/** 分类树查询 */
export function getCategoryTree() {
  return get<CategoryVO[]>('/product/category/tree')
}

/** 新增分类 */
export function createCategory(data: CategorySaveDTO) {
  return post<void>('/product/category', data)
}
```

```typescript
// src/api/product/brand.ts
import { get, post, put, del } from '@/utils/request'
import type { PageVO } from '@/api/types/common'
import type { BrandVO, BrandSaveDTO, BrandQueryDTO, BrandRelationVO } from '@/api/types/product'

/** 分页查询品牌 */
export function getBrandPage(params: BrandQueryDTO) {
  return get<PageVO<BrandVO>>('/product/brand', { params })
}

/** 品牌详情 */
export function getBrandDetail(id: string) {
  return get<BrandVO>(`/product/brand/${id}`)
}

/** 删除品牌 */
export function deleteBrand(id: string) {
  return del<void>(`/product/brand/${id}`)
}
```

组织原则：

- `api/product/` 对应后端 `mall-product` 服务
- `category.ts` 对应 `CategoryController`，`brand.ts` 对应 `BrandController`
- URL 路径 `/product/brand` 中的 `/product` 是网关路由前缀，`/brand` 是 Controller 的 `@RequestMapping`

### 4.2 函数命名规范

| 操作 | 命名 | HTTP 方法 | 示例 |
|------|------|------|------|
| 查询列表/分页 | `getXxxPage` / `getXxxList` | GET | `getBrandPage` |
| 查询详情 | `getXxxDetail` / `getXxxById` | GET | `getBrandDetail` |
| 查询树形 | `getXxxTree` | GET | `getCategoryTree` |
| 新增 | `createXxx` | POST | `createBrand` |
| 修改 | `updateXxx` | PUT | `updateBrand` |
| 删除 | `deleteXxx` | DELETE | `deleteBrand` |
| 批量删除 | `batchDeleteXxxs` | POST/DELETE | `batchDeleteBrands` |

命名用动词开头，语义清晰，一看就知道干什么。对应后端 Controller 的 `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`。

### 4.3 对比后端分层

| 后端 | 前端 | 职责 |
|------|------|------|
| `Controller` | `api/xxx/yyy.ts` | 定义接口路径、HTTP 方法、参数 |
| `Service` | （无对应） | 前端没有业务逻辑层，API 函数直接返回数据 |
| `VO`/`DTO` | `api/types/*.ts` | 数据结构定义 |
| `R<T>` 统一响应 | 拦截器自动剥离 | 前端不关心 `R<T>`，只拿 `data` |

后端有 Controller→Service→Mapper 三层，前端简化为「API 函数→组件」两层。因为前端不做持久化、不做事务，只做请求转发和数据展示，不需要 Service 层。

## 5. 前后端协作约定

> 对照 `docs/frontend/overview.md` 第六节，这里讲清楚每条约定背后的设计。

### 5.1 统一 R<T> 响应格式

后端所有接口返回 `R<T>`，HTTP 状态码恒为 200，业务成败由 `R.code` 表达。前端响应拦截器据此判断：

```typescript
if (res.code === 200) return res.data   // 成功
else ElMessage.error(res.msg)            // 失败
```

**为什么不用 HTTP 状态码表达业务错误？** 因为网关/CDN 会拦截 4xx/5xx，可能导致前端拿不到响应体。电商系统业务错误种类繁多（库存不足、优惠券已领完），统一 200 + 业务码是通行做法。

### 5.2 PageVO<T> 分页

分页接口统一返回 `R<PageVO<T>>`，前端拦截器剥成 `PageVO<T>`：

```typescript
const page = await getBrandPage(params)
// page: PageVO<BrandVO>
// page.records: BrandVO[]    ← 数据列表
// page.total: string         ← 总数（需 Number() 转换）
```

### 5.3 错误码 + msg

后端用 `ResultCode` 枚举管理错误码（200 成功，400+ 客户端错误，500+ 服务端错误，40001+ 按服务分段）。前端不关心具体码值，只看 `code === 200` 判断成败，失败时直接展示 `msg`。

前端不需要维护一份错误码对照表，因为 `msg` 已经是给人看的文案。

### 5.4 Long→String 精度处理

后端 `JacksonConfig` 全局配置 `Long→String` 序列化，前端所有 ID 和分页字段用 `string` 接收。这是贯穿全栈的约定，从 `R<T>` 的泛型到每个 VO 的 `id: string` 都要遵守。

### 5.5 网关路由

```
前端 baseURL:  http://localhost:1000/api/product/brand
                           ↓
网关 Gateway:  StripPrefix=1，去掉 /api
                           ↓
后端服务:      /product/brand → BrandController
```

- 开发环境前端直连网关 `localhost:1000`，无需 Vite proxy
- 网关 `StripPrefix=1` 去掉 `/api` 前缀后转发到 `mall-product` 服务
- 生产环境同源部署，`baseURL=/api`，由 Nginx 反代到网关

### 5.6 CORS 配置

网关已配置允许 `http://localhost:5173~5176`（Vite 默认端口及备用端口），开发环境前端直连网关不会跨域。生产环境同源部署，不存在 CORS 问题。

## 6. 完整请求流程

### 6.1 流程图

以品牌分页查询 `getBrandPage(params)` 为例，从组件调用到响应处理的完整链路：

```
┌─────────────────────────────────────────────────────────────────┐
│ Vue 组件 (BrandList.vue)                                        │
│   const page = await getBrandPage({ pageNum: 1, pageSize: 10 }) │
└──────────────────────────┬──────────────────────────────────────┘
                           │ 调用
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│ api/product/brand.ts                                            │
│   get<PageVO<BrandVO>>('/product/brand', { params })           │
└──────────────────────────┬──────────────────────────────────────┘
                           │ 调用封装方法
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│ utils/request.ts → get<T>()                                     │
│   request.get(url, config) as unknown as Promise<T>            │
└──────────────────────────┬──────────────────────────────────────┘
                           │ 进入 Axios 实例
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│ 请求拦截器                                                       │
│   （当前空实现，未来注入 Authorization: Bearer <token>）          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ 拼接 baseURL
                           ▼
              http://localhost:1000/api/product/brand?pageNum=1&pageSize=10
                           │ HTTP GET
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│ Spring Cloud Gateway (端口 1000)                                │
│   StripPrefix=1 去掉 /api → 转发到 mall-product 服务            │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│ mall-product: BrandController.list()                            │
│   return R.ok(brandService.pageQuery(query))                   │
│                                                                 │
│ 响应体:                                                          │
│   { "code": 200, "msg": "success",                              │
│     "data": { "records": [...], "total": "100", ... } }         │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP 200 + R<PageVO<BrandVO>>
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│ 响应拦截器                                                       │
│   const res = response.data as R<unknown>                       │
│   res.code === 200 → return res.data  ← 剥离 R<T> 外壳          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ 返回 PageVO<BrandVO>（运行时）
                           │ 类型断言为 Promise<PageVO<BrandVO>>
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│ 组件拿到数据                                                     │
│   page.records: BrandVO[]    ← 直接用，无需 res.data.data       │
│   page.total: string         ← Number(page.total) 转换          │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 失败流程对比

如果后端返回业务错误（如 `code=50001` 品牌名已存在）：

```
后端响应: { "code": 50001, "msg": "品牌名已存在", "data": null }
           │
           ▼
响应拦截器: res.code !== 200
           │
           ├── ElMessage.error("品牌名已存在")   ← 统一弹提示
           └── Promise.reject(new Error(...))   ← 组件 catch 能捕获
```

如果是网络错误（如断网）：

```
Axios 抛出: Error("Network Error")
           │
           ▼
错误拦截器:
           │
           ├── 401? → 跳登录页（TODO）
           └── ElNotification.error({ title: "请求错误", message: "Network Error" })
```

组件代码只需处理成功分支，错误已被拦截器统一兜底：

```typescript
try {
  const page = await getBrandPage(params)
  // 只写成功逻辑
  tableData.value = page.records
} catch {
  // 错误已被拦截器弹窗，这里可以不处理
  // 需要时也可做额外处理（如重置 loading）
}
```

## 小结

| 关键点 | 说明 |
|------|------|
| Axios 实例 | `axios.create` + `baseURL` + `timeout`，隔离全局配置 |
| 请求拦截器 | 统一注入 Token（当前空实现，鉴权后启用） |
| 响应拦截器 | 剥离 `R<T>` 外壳，`code===200` 返回 `data`，否则弹 `ElMessage` |
| 错误拦截器 | 网络错误弹 `ElNotification`，401 跳登录（TODO） |
| 泛型方法 | `get<T>`/`post<T>` 等，`as unknown as Promise<T>` 修正拦截器造成的类型断层 |
| 类型定义 | `R<T>`/`PageVO<T>` 对应后端，ID 用 `string` 避免雪花 ID 精度丢失 |
| 模块组织 | `api/{模块}/{功能域}.ts` 对应后端 Controller，类型集中放 `api/types/` |
| 协作约定 | 统一 `R<T>`、`PageVO`、Long→String、网关 StripPrefix=1 |

理解了这一层，你就掌握了前端与后端交互的全部约定。下一篇 [10-功能页面实现](./10-feature-pages.md) 会讲解如何在具体页面中组合使用这些 API 函数与通用组件。
