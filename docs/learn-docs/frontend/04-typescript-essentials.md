# TypeScript 在 Vue 中的实践

> 面向有 Java/Spring 后端经验、刚接触前端的开发者。TypeScript 的类型系统可以类比为 Java 的泛型与接口——理解了这层对应关系，TS 学起来会非常自然。
> 文中代码示例多数取自本项目的 `mall-admin-frontend/src/`，可直接对照源码阅读。

## 1. 为什么需要 TypeScript

### 1.1 JavaScript 的痛点：运行时才报错

JavaScript 是**动态类型**语言：变量没有类型约束，运行到哪行才知道对不对。这对习惯 Java 编译期检查的后端开发者来说，非常难受。

```js
// ❌ JS：写错了也不报错，运行时才炸
function getUser(id) {
  return fetch('/api/user/' + id).then(r => r.json())
}
const user = getUser(123)       // 忘了 await，user 是个 Promise
console.log(user.name)          // undefined，但不会报错
```

Java 之所以让人安心，是因为编译期就能挡住这类错误：

```java
// ✅ Java：编译期类型检查
User user = getUser(123);        // 返回 User，不是 CompletableFuture
System.out.println(user.getName());  // 编译器保证 getName 存在
```

### 1.2 TypeScript = JavaScript + 静态类型

TypeScript（简称 TS）给 JS 加上了**编译期类型检查**：写代码时就告诉你哪里类型不对，不用等到运行时。

```ts
// ✅ TS：编译期就能发现问题
interface User { id: number; name: string }
function getUser(id: number): Promise<User> {
  return fetch('/api/user/' + id).then(r => r.json())
}
const user = getUser(123)        // 类型是 Promise<User>，不是 User
console.log(user.name)           // ❌ TS 报错：Promise 上没有 name 属性
```

| | JavaScript | TypeScript |
|------|------|------|
| 类型检查 | 运行时 | 编译期 |
| 发现 bug 的时机 | 上线后用户触发 | 写代码时（IDE 红线） |
| 重构支持 | 弱（不敢改） | 强（改错就报错） |
| 类比 Java | — | Java 的编译期类型检查 |

> **一句话理解**：TS 之于 JS，就像 Java 编译器之于 `.java` 文件——把错误拦截在运行之前。

## 2. 基础类型

### 2.1 基本类型

```ts
let name: string = '小米商城'
let count: number = 10
let enabled: boolean = true
let empty: null = null
let nothing: undefined = undefined
```

| TS 类型 | Java 对应 | 说明 |
|------|------|------|
| `string` | `String` | 字符串（注意小写） |
| `number` | `int/long/double` | 统一用 number，不区分整数/浮点 |
| `boolean` | `boolean` | 布尔 |
| `null` / `undefined` | `null` | JS 有两个空值，TS 也分别对待 |

### 2.2 数组与元组

```ts
// 数组：两种写法等价
let ids: number[] = [1, 2, 3]
let names: Array<string> = ['小米', '红米']   // 类似 Java 的 List<String>

// 元组（tuple）：固定长度、固定类型的数组
let pair: [string, number] = ['小米', 10]
```

> 元组类似 Java 的「固定长度记录」，实际项目中用得少，了解即可。

### 2.3 联合类型

一个变量可以是多种类型之一，用 `|` 连接：

```ts
let id: string | number = 123   // 可以是 string 或 number
id = 'abc'                       // 也合法
```

项目里的分页字段就是典型——后端把 `Long` 序列化成了 `string`，前端需要用 `Number()` 转换：

```ts
// src/api/types/common.ts
export interface PageVO<T> {
  records: T[]
  total: string      // 后端 Long→String，前端拿到的是 string
  size: string
  current: string
  pages: string
}
// 使用时：Number(res.total) 转成数字
```

### 2.4 字面量类型

把变量的值限定在几个具体值中，类似 Java 的枚举：

```ts
type Align = 'left' | 'center' | 'right'
let align: Align = 'center'      // 只能是这三个值之一
```

项目 PageTable 的列配置就用了字面量类型：

```ts
// src/components/PageTable/index.vue
interface Column {
  align?: 'left' | 'center' | 'right'   // 只允许这三个值
  fixed?: boolean | 'left' | 'right'    // 联合字面量
}
```

### 2.5 any vs unknown vs never

| 类型 | 含义 | 类比 Java | 能否直接使用 |
|------|------|------|------|
| `any` | 放弃类型检查 | `Object`（但不推荐） | ✅ 可以，等于关掉检查 |
| `unknown` | 类型未知，用之前必须断言 | `Object`（更安全） | ❌ 必须先收窄 |
| `never` | 永不出现的值 | `throw` 的返回类型 | — |

```ts
let a: any = 1
a.foo()              // 不报错，但运行时炸（危险！）

let u: unknown = 1
// u.foo()           // ❌ TS 报错：unknown 上不能调用方法
if (typeof u === 'string') {
  u.toUpperCase()    // ✅ 收窄后才能用
}
```

> **建议**：项目里需要「任意类型」时优先用 `unknown` 而不是 `any`，逼自己显式收窄，更安全。`useTable.ts` 中用了 `Record<string, any>` 表示任意搜索参数，属于动态性较强的场景，可以接受。

## 3. 接口与类型别名

### 3.1 interface：定义对象形状

`interface` 用来描述一个对象有哪些字段、什么类型——类比 Java 的 POJO / 接口定义：

```ts
// TS
interface BrandVO {
  id: string
  name: string
  logo: string
  sort: number
  version?: number          // 可选属性，类比 Java 中可空字段
}
```

```java
// 对应的 Java POJO
public class BrandVO {
    private String id;
    private String name;
    private String logo;
    private Integer sort;
    private Integer version;  // 可为 null
}
```

> TS 的 `interface` 只在编译期存在，编译成 JS 后会完全消失——它纯粹是给类型检查器看的，不产生运行时代码。这点和 Java 接口不同（Java 接口有字节码）。

### 3.2 可选属性 `?` 与只读 `readonly`

```ts
interface CategoryVO {
  id: string
  name: string
  /** 可选属性：可能不存在 */
  icon?: string
  /** 只读属性：赋值后不能再改 */
  readonly level: number
}

const cat: CategoryVO = { id: '1', name: '手机', level: 1 }
// cat.level = 2   // ❌ 只读属性不能重新赋值
```

项目里的 `CategoryUpdateDTO` 大量使用可选属性——更新时只传需要改的字段：

```ts
// src/api/types/product.ts
export interface CategoryUpdateDTO {
  id: string        // 必传：知道改谁
  name?: string        // 可选：不改就不传
  sort?: number
  icon?: string
  productUnit?: string
}
```

### 3.3 type 类型别名

`type` 给一个类型起名字，能力比 `interface` 更广（能定义联合、元组、基本类型别名）：

```ts
type ID = string | number
type Status = 'create' | 'update' | 'delete'
type Callback = (data: BrandVO) => void
```

### 3.4 interface vs type 怎么选

| 能力 | interface | type |
|------|------|------|
| 描述对象形状 | ✅ 推荐 | ✅ 可以 |
| 联合类型 / 字面量 | ❌ | ✅ |
| 声明合并（同名自动合并） | ✅ | ❌ |
| extends 继承 | ✅ | ✅（用 `&` 交叉类型） |

> **项目约定**：描述对象（VO/DTO）用 `interface`，定义联合/工具类型用 `type`。本项目 `common.ts`、`product.ts` 全部用 `interface` 定义数据结构，因为它们就是「对象形状」。

## 4. 泛型（重点）

### 4.1 为什么需要泛型

泛型让你写一份代码、适配多种类型——和 Java 泛型完全一个思路。

```java
// Java：List<T> 不知道 T 是什么，但保证里面都是同一种类型
List<BrandVO> brands = new ArrayList<>();
BrandVO b = brands.get(0);   // 不用强转
```

TS 同理：

```ts
// TS：泛型函数，T 是类型参数
function getFirst<T>(arr: T[]): T {
  return arr[0]
}
const b = getFirst<BrandVO>(brands)   // 返回 BrandVO，不用断言
const n = getFirst<number>([1, 2, 3]) // 返回 number
```

没有泛型，你得为每种类型写一个函数，或者用 `any` 放弃类型检查——两种都不好。

### 4.2 泛型接口

项目最典型的泛型接口是统一响应结构 `R<T>`，对应后端 `com.mymall.common.result.R`：

```ts
// src/api/types/common.ts
export interface R<T> {
  code: number
  msg: string
  data: T      // data 的类型由调用方决定
}
```

```java
// 对应 Java
public class R<T> {
    private Integer code;
    private String msg;
    private T data;
}
```

使用时指定 `T`：

```ts
const res: R<BrandVO> = await getBrand(id)
// res.data 的类型是 BrandVO，访问 res.data.name 有类型提示
```

分页结构 `PageVO<T>` 也是泛型接口，`T` 表示列表元素的类型：

```ts
export interface PageVO<T> {
  records: T[]     // 列表数据，元素类型是 T
  total: string
  // ...
}
```

### 4.3 泛型函数：项目中的 get/post 封装

`request.ts` 把 axios 封装成泛型函数，调用时指定返回类型，享受类型提示：

```ts
// src/utils/request.ts
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request.get(url, config) as unknown as Promise<T>
}
```

```ts
// 业务调用：T = BrandVO，返回 Promise<BrandVO>
import { get } from '@/utils/request'
import type { BrandVO } from '@/api/types/product'

const brand = await get<BrandVO>('/product/brand/1')
//    ^^^^^ 推断为 BrandVO，brand.name 有提示
```

> 类比 Java Feign：`R<BrandVO> res = brandFeign.get(id)` 中 `BrandVO.class` 就是泛型参数，TS 的 `get<BrandVO>` 是同一个意思。

### 4.4 泛型 Composable：useTable\<T\>

`useTable<T>` 是项目里泛型应用最精彩的例子——一个函数管理所有分页表格的状态，`T` 是行数据类型：

```ts
// src/composables/useTable.ts
export function useTable<T>(fetchFn: (params: Record<string, any>) => Promise<PageVO<T>>) {
  const data: Ref<T[]> = ref([])   // 行数据数组，类型是 T[]
  const loading = ref(false)
  const total = ref(0)
  // ...
  return { data, loading, total, /* ... */ }
}
```

使用时，`T` 由传入的 `fetchFn` 自动推断：

```ts
// 品牌列表：T 推断为 BrandVO
const { data, loading, loadData } = useTable<BrandVO>((params) =>
  get<PageVO<BrandVO>>('/product/brand/page', { params })
)
// data 的类型是 Ref<BrandVO[]>，data.value[0].name 有提示
```

> 不写泛型的话，`data` 只能是 `any[]`，丢掉所有类型提示——这正是项目大量使用泛型的原因：**一次封装，处处类型安全**。

## 5. Vue + TypeScript 实践

### 5.1 `<script setup lang="ts">`

Vue 单文件组件加 `lang="ts"` 就能使用 TS。所有变量、函数都有类型检查：

```vue
<script setup lang="ts">
import { ref } from 'vue'
const count = ref(0)         // 自动推断为 Ref<number>
const name = ref<string>('')  // 也可显式指定泛型
</script>
```

### 5.2 defineProps 泛型方式

Vue 3 推荐用泛型语法定义 props，比运行时声明更直观，且类型推断更完整：

```ts
// src/components/PageTable/index.vue
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
    searchFields: () => []
  }
)
```

- `defineProps<{ ... }>()`：用对象类型字面量描述 props，可选属性 `?` 自动变成「非必填 prop」。
- `withDefaults(…, { … })`：为可选 prop 提供默认值（TS 的 `?` 只是「可选」，不自动给默认值）。

> 类比 Java：相当于给方法参数列表加类型 + 默认值。`defineProps` 是 Vue 的编译宏，不需要 import。

### 5.3 defineEmits 类型化事件

```ts
// src/components/PageTable/index.vue
const emit = defineEmits<{
  (e: 'selection-change', rows: any[]): void
}>()
// 调用：emit('selection-change', selectedRows)
```

```ts
// src/components/FormDialog/index.vue
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void   // v-model 同步
  (e: 'success'): void                            // 提交成功
}>()
```

> 这种「函数签名重载」写法定义了「事件名 + 参数类型」。如果 emit 时参数类型不对，TS 会报错。

### 5.4 ref\<T\> 与 computed

```ts
import { ref, computed } from 'vue'

// ref<T>() 显式指定泛型，适合初始值为空、推断不准的场景
const formRef = ref<FormInstance>()         // 初始 undefined，需显式指定
const tableData = ref<BrandVO[]>([])        // 空数组，指定元素类型

// computed 通常能自动推断，无需写泛型
const total = computed(() => tableData.value.length)   // 推断为 ComputedRef<number>
```

> **经验**：初始值能体现类型时（`ref(0)`、`ref('')`）让 TS 自动推断；初始值为 `undefined` 或空数组时，显式写 `ref<T>()`。

### 5.5 泛型组件：`<script setup generic="T">`

普通组件用 `defineProps<{...}>` 时，props 类型是固定的。如果组件需要「调用方决定类型」，就要用泛型组件。FormDialog 就是这么做的：

```vue
<!-- src/components/FormDialog/index.vue -->
<script setup lang="ts" generic="T extends Record<string, any>">
const props = defineProps<{
  modelValue: boolean
  title: string
  initialData: T                    // 表单数据类型由父组件决定
  submit: (data: T) => Promise<void> // 提交函数的参数也是 T
}>()

const formData = ref<T>({ ...props.initialData })
</script>
```

```ts
// 父组件使用：T 推断为 BrandSaveDTO
<FormDialog
  :initial-data="brandForm"
  :submit="handleSave"
  ...
/>
```

> 类比 Java：`generic="T"` 相当于把组件变成 `class FormDialog<T extends Record<string, any>>`，`T` 由父组件传入的数据推断出来。`T extends Record<string, any>` 是泛型约束，限制 `T` 必须是「字符串键的对象」。

## 6. 类型断言与类型守卫

### 6.1 类型断言 `as`

当你比 TS 更清楚某个值的类型时，用 `as` 告诉它：

```ts
const res = response.data as R<unknown>   // 告诉 TS 这是 R<unknown>
```

> 类比 Java 的强制类型转换 `(BrandVO) obj`，但 TS 的 `as` 只在编译期起作用，不改变运行时行为，且不会抛 ClassCastException（不像 Java）。

### 6.2 双重断言 `as unknown as T`

当两个类型差异较大，直接 `as` 会报错时，先转 `unknown` 再转目标类型——项目 `request.ts` 就这么干：

```ts
// src/utils/request.ts
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request.get(url, config) as unknown as Promise<T>
}
```

**为什么需要双重断言？**

响应拦截器返回的是 `res.data`（被识别为 `unknown`），而 `request.get` 返回 `Promise<AxiosResponse>`。这两者差异太大，直接 `as Promise<T>` TS 不让转。先 `as unknown`（任何东西都能转 unknown），再 `as Promise<T>`，就绕过了限制。

> 这是项目里为了「泛型 + 拦截器剥离」做的妥协：拦截器改了返回值类型，但 axios 的类型定义不知道。实际运行时返回的确实是 `T`（已剥离 `R<T>` 外壳），断言是安全的。

### 6.3 类型守卫：typeof / instanceof

类型守卫让 TS 在某个代码块内自动收窄类型：

```ts
// typeof：判断基本类型
function format(val: string | number) {
  if (typeof val === 'string') {
    return val.toUpperCase()   // 这里 val 是 string
  }
  return val.toFixed(2)        // 这里 val 是 number
}

// instanceof：判断实例类型（类比 Java 的 instanceof）
if (error instanceof Error) {
  console.log(error.message)   // 这里 error 是 Error
}
```

> 类比 Java：`if (obj instanceof BrandVO b)` 的模式匹配，TS 的类型守卫让分支内类型自动收窄。

## 7. 工具类型

工具类型是 TS 内置的「类型转换函数」，接收类型参数，返回新类型——类似 Java 的工具方法，但作用于类型层面。

| 工具类型 | 作用 | 类比 |
|------|------|------|
| `Partial<T>` | 所有属性变可选 | 更新时部分字段传值 |
| `Required<T>` | 所有属性变必填 | `Partial` 的反操作 |
| `Pick<T, K>` | 挑选部分属性 | 类比只取几个字段 |
| `Omit<T, K>` | 排除部分属性 | `Pick` 的反操作 |
| `Record<K, V>` | 键值对类型 | 类比 `Map<K, V>` |
| `Readonly<T>` | 所有属性变只读 | 不可变对象 |

```ts
interface BrandVO {
  id: string
  name: string
  logo: string
  sort: number
}

// Partial：全部可选
type BrandPatch = Partial<BrandVO>
// 等价于 { id?: string; name?: string; logo?: string; sort?: number }

// Pick：只取 name 和 sort
type BrandBrief = Pick<BrandVO, 'name' | 'sort'>
// 等价于 { name: string; sort: number }

// Omit：排除 logo
type BrandNoLogo = Omit<BrandVO, 'logo'>
// 等价于 { id: string; name: string; sort: number }
```

### 项目实例：Record\<string, any\>

`Record<K, V>` 表示「键类型为 K、值类型为 V 的对象」，类比 Java 的 `Map<K, V>`。项目里用它表示「任意搜索参数」：

```ts
// src/composables/useTable.ts
const searchParams = ref<Record<string, any>>({})
// 等价于 { [key: string]: any }，一个键为字符串、值为任意类型的对象
```

```ts
// src/components/PageTable/index.vue
fetch: (params: Record<string, any>) => Promise<PageVO<any>>
// params 是任意键值对，如 { pageNum: 1, pageSize: 10, name: '小米' }
```

> 为什么不用具体接口？因为搜索条件因页面而异（品牌按名称搜、分类按层级搜），用 `Record<string, any>` 才能通用。代价是失去搜索字段的类型提示——这是通用性与类型安全的权衡。

## 8. tsconfig 配置

### 8.1 三文件体系（Project References）

项目用三个 tsconfig 文件分工，这是 Vue + Vite 的标准配置：

```
tsconfig.json          # 入口，只做引用聚合，不含实际配置
├── tsconfig.app.json  # 应用代码（src/**）的配置
└── tsconfig.node.json # 构建/配置文件（vite.config.ts）的配置
```

```jsonc
// tsconfig.json —— 入口，只引用，不编译
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

> 类比 Maven 多模块：根 `tsconfig.json` 像 parent pom，不写实际配置，只管理子模块。`references` 让两个配置独立编译、互不干扰（应用代码是 DOM 环境，Vite 配置是 Node 环境）。

### 8.2 strict 模式

```jsonc
// tsconfig.node.json（节选）
{
  "compilerOptions": {
    "strict": true,                  // 开启所有严格检查
    "noUnusedLocals": true,          // 未使用的变量报错
    "noUnusedParameters": true,      // 未使用的参数报错
    "noFallthroughCasesInSwitch": true // switch 必须有 break
  }
}
```

`strict: true` 是一组严格选项的总开关，开启后：

- **隐式 `any` 报错**：函数参数不写类型会报错（`noImplicitAny`）
- **`null`/`undefined` 严格**：不能把 `null` 赋给非空类型（`strictNullChecks`）
- **`this` 严格**：函数内 `this` 必须有明确类型

> 类比 Java：Java 默认就是「strict 模式」——变量必须声明类型、空指针编译期不拦但运行时拦。TS 开了 strict 才接近 Java 的严格程度。**生产项目必须开 strict**。

### 8.3 路径别名 `@/*`

```jsonc
// tsconfig.app.json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  }
}
```

配置后，`@/api/types/common` 等价于 `src/api/types/common`。Vite 配置里也要做对应映射（`resolve.alias`），否则运行时找不到模块。

```ts
// 用别名，简短清晰
import type { R, PageVO } from '@/api/types/common'
import { get } from '@/utils/request'

// 不用别名，相对路径层级深时容易乱
import type { R } from '../../../api/types/common'
```

> 类比 Java：相当于 Maven 的 `<package>` 解析，`@/` 就是 `src/` 的别名，省去数 `../../../` 的痛苦。

## 9. 小结

| 概念 | TS 写法 | Java 类比 |
|------|------|------|
| 静态类型 | `let x: number` | `int x` |
| 接口 | `interface BrandVO {...}` | `class BrandVO` / POJO |
| 泛型 | `R<T>`、`get<T>()` | `R<T>`、`<T> T get()` |
| 联合类型 | `string \| number` | 无直接对应 |
| 可选属性 | `icon?: string` | 可空字段 |
| 只读 | `readonly id: string` | `final` 字段 |
| 类型断言 | `data as R<T>` | `(R) data` 强转 |
| 工具类型 | `Partial<T>`、`Record<K,V>` | 工具方法作用于类型 |
| 泛型组件 | `<script setup generic="T">` | `class Component<T>` |

**学习路径建议**：

1. 先掌握 `interface` + 基本类型——能看懂项目里的 VO/DTO 定义
2. 再学泛型——理解 `R<T>`、`PageVO<T>`、`get<T>()` 的设计
3. 最后学 Vue 的类型化用法——`defineProps`、`ref<T>`、泛型组件

对照项目代码阅读：`api/types/` 是接口定义集中地，`utils/request.ts` 是泛型函数范例，`composables/useTable.ts` 和 `components/FormDialog/` 是泛型 + Vue 实践的精华。
