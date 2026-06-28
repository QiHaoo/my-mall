# 前端基础设施设计

> 前面 6 篇讲的是「Vue / Router / Pinia / TS / Element Plus / Vite 是什么」，这篇讲「项目里它们为什么这么搭」。
>
> 对照 `mall-admin-frontend` 的真实代码，讲每个设计决策背后的取舍。

## 1. 项目脚手架选型

先看 `package.json`，这是整个前端的「依赖清单」：

```json
{
  "dependencies": {
    "axios": "^1.7.9",
    "element-plus": "^2.9.1",
    "@element-plus/icons-vue": "^2.3.1",
    "pinia": "^2.3.0",
    "vue": "^3.5.13",
    "vue-router": "^4.5.0"
  },
  "devDependencies": {
    "vite": "^6.0.0",
    "sass": "^1.83.0",
    "typescript": "~5.7.2",
    "unplugin-auto-import": "^19.0.0",
    "unplugin-vue-components": "^28.0.0",
    "eslint": "^9.17.0",
    "prettier": "^3.4.2"
  }
}
```

### 1.1 技术选型理由

| 依赖 | 作用 | 为什么选它 |
|------|------|----------|
| Vue 3.5 | 框架 | Composition API + `<script setup>`，对后端同学最直观 |
| TypeScript | 类型 | 后端有 Java 泛型基础，TS 是「前端的泛型」，接受度高 |
| Vite 6 | 构建 | 开发期按需编译秒级启动，见 06 篇 |
| Element Plus | UI 库 | Vue 3 生态最成熟的企业级组件库，开箱即用 |
| Pinia | 状态管理 | Vue 3 官方推荐，比 Vuex 简单 |
| Axios | HTTP | 拦截器机制成熟，和后端 `R<T>` 包装对接自然 |
| SCSS | 样式预处理 | 变量 + 嵌套 + mixin，比纯 CSS 好维护 |

这套组合和 `docs/frontend/overview.md` 定义的技术栈完全一致——**先定规范，再落地代码**，而不是写到哪里用到什么就装什么。

### 1.2 pnpm vs npm / yarn

后端同学熟悉 Maven，那 `package.json` 就是前端的 `pom.xml`，而包管理器是前端的「Maven 本身」。项目用 pnpm，原因看对比：

| | npm | yarn | pnpm |
|------|------|------|------|
| 安装速度 | 慢 | 中 | 快（硬链接复用） |
| 磁盘占用 | 每个项目一份 | 每个项目一份 | 全局 store，多项目共享 |
| 幽灵依赖 | ❌ 有（扁平化 node_modules） | ❌ 有 | ✅ 无（严格的依赖树） |
| 单仓库多包 | 需要额外工具 | workspaces | workspaces 原生支持 |

**幽灵依赖**是关键：npm/yarn 会把所有依赖（包括子依赖）拍平到 `node_modules` 根目录，导致你能 `import` 一个没在 `package.json` 里声明的包。pnpm 用软链接 + 严格的 `node_modules` 结构，**只能用到自己声明过的依赖**，更安全。

> 后端类比：pnpm 的全局 store 就像 Maven 的本地仓库 `~/.m2/repository`，多个项目共享同一份制品，省磁盘又快。

### 1.3 为什么不用 Tailwind CSS

`package.json` 里没有 Tailwind，样式用的是 Element Plus + SCSS 变量系统。这是刻意的取舍：

| | Tailwind | Element Plus + SCSS |
|------|------|------|
| 理念 | 原子类（`flex p-4 text-center`） | 语义化组件 + 变量主题 |
| 适合场景 | 高度定制化的 C 端页面 | 表单/表格密集的管理后台 |
| 学习成本 | 要记一堆类名 | 后端同学看组件名就懂 |
| 视觉一致性 | 完全靠开发者自觉 | 组件库自带规范 |

**取舍理由**：管理后台 90% 是表单 + 表格 + 弹窗，用 Element Plus 的 `<el-table>`、`<el-form>` 直接搞定，比手写 Tailwind 类快得多。而且企业级后台要的是「统一感」——所有页面按钮圆角、间距、配色一致，组件库 + SCSS 变量天然保证这一点。Tailwind 更适合需要「设计自由度」的 C 端项目。

后面第 3 节会讲，项目自己写了一组轻量工具类（`utilities.scss`）补上 Tailwind 最常用的那几个原子能力，够用且不喧宾夺主。

## 2. 目录结构设计

### 2.1 src 目录全貌

```
src/
├── api/                  # 接口层（按业务模块分文件）
│   ├── product/
│   │   ├── brand.ts
│   │   └── category.ts
│   └── types/            # 接口类型定义（对应后端 DTO）
│       ├── common.ts     # R<T>、PageVO<T>、PageQuery
│       └── product.ts
├── assets/styles/        # 全局样式
├── components/           # 全局通用组件（跨模块复用）
│   ├── FormDialog/
│   └── PageTable/
├── composables/          # 可复用逻辑（useTable / useDialog）
├── layouts/              # 布局组件
│   ├── components/       # 布局子组件（Sidebar/Navbar/...）
│   └── AdminLayout.vue
├── router/               # 路由配置
├── stores/               # Pinia 全局状态
├── utils/                # 工具函数（request.ts / tree.ts）
├── views/                # 页面（按业务模块组织）
│   ├── error/404.vue
│   └── product/
│       ├── brand/
│       │   ├── components/   # 模块专属组件
│       │   └── index.vue
│       └── category/
│           ├── components/
│           └── index.vue
├── App.vue
└── main.ts
```

### 2.2 与后端分包架构的对应

后端同学对分层不陌生，前端这套目录其实就是后端分层的「前端版」：

| 前端目录 | 后端对应 | 职责 |
|---------|---------|------|
| `views/` | `controller/` | 页面入口，负责展示和交互编排 |
| `api/` | `feign/`（远程调用客户端） | 封装 HTTP 请求，对应后端接口 |
| `api/types/` | `dto/` / `vo/` | 数据结构定义，对应后端 DTO |
| `components/` | 公共工具类 | 跨模块复用的 UI 组件 |
| `composables/` | 公共 Service 工具 | 跨模块复用的逻辑（如分页逻辑） |
| `stores/` | （无直接对应） | 前端特有的全局状态 |
| `utils/` | `util/` | 纯工具函数 |
| `router/` | （无直接对应） | 前端特有的路由 |
| `layouts/` | （无直接对应） | 页面骨架 |

**关键差异**：后端的 Service 层在前端被拆成了两半——`api/` 负责「发请求拿数据」，`composables/` 负责「在页面里复用请求 + 状态逻辑」（比如 `useTable` 把分页 + 加载 + 刷新封装成一个钩子）。

### 2.3 为什么模块专属组件放在 views/{module}/components/

看 `views/product/brand/` 的结构：

```
brand/
├── components/
│   ├── BrandForm.vue            # 品牌新增/编辑表单
│   └── BrandRelationDialog.vue  # 品牌关联分类弹窗
└── index.vue                    # 品牌列表页
```

`BrandForm` 只在品牌页用，为什么要放 `views/product/brand/components/` 而不是全局 `src/components/`？

**因为复用范围决定归属位置**：

- 全局 `src/components/` 放的是「**任何模块都可能用**」的组件，比如 `PageTable`（分页表格）、`FormDialog`（表单弹窗）——分类页、品牌页、未来的订单页都会用。
- `views/{module}/components/` 放的是「**只有这个模块用**」的组件，比如 `BrandForm` 只有品牌页用，`CategoryNode` 只有分类树用。

好处是：删掉某个模块时，直接删整个 `views/{module}/` 目录就行，不会有孤儿组件遗留在全局 `components/` 里。这和后端「模块内私有类放模块包，公共类放 common」是一个思路。

## 3. 样式体系设计

`src/assets/styles/` 下有 5 个文件，各司其职：

```
assets/styles/
├── variables.scss       # SCSS 变量（颜色/间距/字号...）
├── reset.scss           # CSS Reset
├── element-plus.scss    # Element Plus 主题覆盖
├── utilities.scss       # 工具类
└── index.scss           # 汇总入口
```

`index.scss` 是入口，把其他三个串起来：

```scss
@use 'reset.scss';
@use 'element-plus.scss';
@use 'utilities.scss';
```

### 3.1 SCSS 变量体系：为什么用 4px 网格

`variables.scss` 定义了全套设计 token：

```scss
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

// ===== 侧边栏 =====
$sidebar-bg: #304156;
$sidebar-text: #bfcbd9;
$sidebar-width: 210px;
$sidebar-collapsed-width: 64px;
```

**为什么间距都用 4 的倍数？** 这叫「4px 网格系统」。人眼对间距的感知是非线性的，4 的倍数能让间距形成清晰的层级（4→8→12→16→24），既不会太碎，又能表达「紧凑 / 常规 / 宽松」三档。

如果允许任意值（比如某个地方写 7px、另一个地方写 9px），整个系统会失去节奏感。统一用变量 `$spacing-*`，从根上保证「全站间距一致」。

> 后端类比：这就像枚举常量替代魔法值。`$spacing-base` 比 `16px` 多一层语义——「这里要的是常规间距」，而不是「这里恰好是 16 像素」。

### 3.2 CSS Reset 的必要性

`reset.scss` 很短，但每一行都有用：

```scss
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
}

body {
  font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  color: #303133;
  background-color: #f5f7fa;
}
```

**为什么需要 Reset？** 不同浏览器对 HTML 元素有默认样式：`<h1>` 有 margin，`<ul>` 有 padding，`<body>` 默认不是 100% 高度。这些「浏览器私货」会导致页面在不同浏览器表现不一。

三个关键点：

1. `box-sizing: border-box` —— 让 `width` 包含 padding 和 border，算尺寸时不用做加法（`100px` 就是 `100px`，不用 `100 - padding - border`）。
2. `html, body, #app { height: 100% }` —— 管理后台是「全屏布局」，侧边栏和内容区都要撑满视口高度，根元素必须是 100% 高，子元素的 `height: 100%` 才有意义。
3. 字体栈 —— 优先用系统字体（macOS 用 PingFang SC，Windows 用微软雅黑），加载快且渲染清晰。

### 3.3 Element Plus 主题覆盖：CSS 变量 vs SCSS 变量

`element-plus.scss` 用的是 **CSS 变量**（不是 SCSS 变量）覆盖主题：

```scss
:root {
  --el-color-primary: #409eff;
  --el-color-success: #67c23a;
  --el-color-warning: #e6a23c;
  --el-color-danger: #f56c6c;
  --el-color-info: #909399;
  --el-border-radius-base: 4px;
}
```

**为什么用 CSS 变量而不是 SCSS 变量覆盖？** 两种方式都能改 Element Plus 主题，但机制不同：

| | SCSS 变量覆盖 | CSS 变量覆盖 |
|------|------|------|
| 机制 | 编译期替换，重新生成组件 CSS | 运行期覆盖，浏览器解析时生效 |
| 需要 | 改 Element Plus 源码的 SCSS 变量 + 重新编译 | 直接写 `:root { --el-xxx }` |
| 改色成本 | 改完要重新 build | 改完刷新即可 |
| 运行时换肤 | ❌ 不支持（编译期已固定） | ✅ 支持（JS 改 CSS 变量即可） |

项目用的是全量引入（`main.ts` 里 `app.use(ElementPlus)` + `import 'element-plus/dist/index.css'`），组件 CSS 是预编译好的成品。这种情况下要覆盖主题，**CSS 变量是最省事的方式**——不用去折腾 Element Plus 的 SCSS 源码，写个 `:root` 就生效。

而 `variables.scss` 里的 `$color-primary` 是给**自己写的样式**用的（比如侧边栏配色），两套变量各管各的，互不冲突。

### 3.4 工具类：为什么不用 Tailwind 的替代方案

`utilities.scss` 自己写了一组高频工具类：

```scss
// ===== Flex 布局 =====
.flex { display: flex; }
.flex-center { display: flex; align-items: center; justify-content: center; }
.flex-between { display: flex; align-items: center; justify-content: space-between; }
.flex-1 { flex: 1; }

// ===== 间距 =====
.mt-8 { margin-top: 8px; }
.mt-16 { margin-top: 16px; }
.mb-16 { margin-bottom: 16px; }

// ===== 页面容器 =====
.app-container { padding: 24px; }
.card-box { background: #fff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.06); }
```

这就是第 1.3 节说的「轻量替代方案」。Tailwind 有几百个原子类，但管理后台真正高频的就这几个：flex 布局、外边距、卡片容器。自己写十几个类够用了，还不用引入 Tailwind 的构建链。

`.app-container` 和 `.card-box` 是项目特有的语义类——每个页面外层套 `.app-container` 留白，内容区套 `.card-box` 做卡片效果，全站统一。

### 3.5 全局注入机制

每次写 SCSS 都要 `@use 'variables.scss'` 太烦。`vite.config.ts` 里配了全局注入：

```ts
css: {
  preprocessorOptions: {
    scss: {
      // 全局注入 SCSS 变量，所有组件可直接使用 $color-primary 等
      additionalData: `@use "@/assets/styles/variables.scss" as *;`
    }
  }
}
```

这行配置的作用：**编译每个 `.vue` / `.scss` 文件前，自动在顶部注入这句话**。所以在任何组件里直接写 `$sidebar-width`、`$spacing-base` 都能用，不用手动 import。

> 后端类比：就像 Spring Boot 的自动配置，你不用每个类都写 `@Import`，框架帮你全局注入好了。

## 4. 布局组件设计

管理后台的布局是「侧边栏 + 顶栏 + 内容区」三段式，项目拆成了 5 个组件：

```
layouts/
├── components/
│   ├── Sidebar.vue     # 侧边栏（菜单）
│   ├── Navbar.vue      # 顶栏（折叠按钮 + 面包屑）
│   ├── Breadcrumb.vue  # 面包屑
│   └── AppMain.vue     # 内容区（router-view）
└── AdminLayout.vue     # 布局容器（组合上面四个）
```

### 4.1 布局拆分思路

`AdminLayout.vue` 是组合入口，只管「怎么摆」：

```vue
<template>
  <div class="admin-layout">
    <Sidebar />
    <div class="main-container">
      <Navbar />
      <AppMain />
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  display: flex;
  height: 100%;
}
.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
</style>
```

整个布局用 flex 实现：外层 `admin-layout` 横向 flex，左边 `Sidebar` 固定宽度，右边 `main-container` 占满剩余；`main-container` 纵向 flex，上面 `Navbar` 固定高度，下面 `AppMain` 占满剩余并滚动。

**为什么拆成 5 个组件而不是全写在一个文件里？** 单一职责。`AdminLayout` 只关心「骨架怎么搭」，`Sidebar` 只关心「菜单怎么渲染」，`Navbar` 只关心「顶栏有什么」。改侧边栏不会碰到顶栏代码，改面包屑不会影响内容区。这和后端「一个类只做一件事」是同一个原则。

### 4.2 侧边栏菜单：从路由配置自动生成

`Sidebar.vue` 没有硬编码菜单项，而是从路由表读取：

```vue
<script setup lang="ts">
const router = useRouter()

// 从路由配置中提取 AdminLayout 的 children 作为菜单项
const menuItems = computed(() => {
  const rootRoute = router.options.routes.find(
    (r) => r.path === '/'
  ) as RouteRecordRaw | undefined
  return rootRoute?.children || []
})

const activeMenu = computed(() => route.path)
</script>

<template>
  <el-menu :default-active="activeMenu" :collapse="appStore.sidebarCollapsed"
           @select="handleSelect">
    <el-menu-item v-for="item in menuItems" :key="item.path"
                  :index="'/' + item.path">
      <el-icon v-if="item.meta?.icon">
        <component :is="iconMap[item.meta.icon]" />
      </el-icon>
      <template #title>{{ item.meta?.title }}</template>
    </el-menu-item>
  </el-menu>
</template>
```

对应的路由配置（`router/index.ts`）：

```ts
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
  }
]
```

**为什么不硬编码菜单？** 因为菜单和路由本质是同一份信息——「有哪些页面、叫什么、图标是什么」。如果菜单硬编码一份、路由配置一份，两边容易不同步：加了新页面忘了加菜单，或者改了路由标题菜单没更新。

这里把菜单的标题、图标放在路由的 `meta` 里，侧边栏直接读路由表渲染——**单一数据源**，加页面只要加一条路由，菜单自动出现。这和后端「配置只在一处定义」的理念一致。

图标用 `iconMap[item.meta.icon]` 动态渲染，`meta.icon` 写的是图标名字符串（如 `'Grid'`），通过 `@element-plus/icons-vue` 的映射找到组件。这样路由配置里不用 import 图标组件，保持配置纯净。

### 4.3 侧边栏折叠状态：Store 管理

折叠按钮在 `Navbar.vue`：

```vue
<el-icon class="collapse-btn" @click="appStore.toggleSidebar()">
  <Fold v-if="!appStore.sidebarCollapsed" />
  <Expand v-else />
</el-icon>
```

状态在 `stores/app.ts`：

```ts
export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('sidebarCollapsed', String(sidebarCollapsed.value))
  }

  return { sidebarCollapsed, toggleSidebar }
})
```

`Sidebar.vue` 读取同一个 store 来控制宽度：

```scss
.sidebar {
  width: $sidebar-width;       // 210px
  &.collapsed {
    width: $sidebar-collapsed-width;  // 64px
  }
}
```

**为什么折叠状态要放 Store 而不是组件局部状态？** 因为折叠状态被两个组件共享：`Navbar` 里点按钮要改状态，`Sidebar` 里要根据状态变宽度。如果放在某个组件里，另一个组件拿不到。放 Pinia Store 里，两个组件都能读写，响应式自动同步。

**为什么要持久化到 localStorage？** 用户体验——用户把侧边栏折叠了，刷新页面后应该保持折叠状态，而不是弹回去。`localStorage` 是浏览器本地存储，刷新不丢。这里没引入持久化插件，手动读写 `localStorage` 更直观，也避免过度封装。

> 后端类比：这就像把用户偏好存到数据库，下次登录恢复。前端没有数据库，`localStorage` 就是浏览器的「本地配置表」。

## 5. 代码规范工具链

### 5.1 Prettier 配置

`.prettierrc.json`：

```json
{
  "semi": false,
  "singleQuote": true,
  "tabWidth": 2,
  "printWidth": 100,
  "trailingComma": "none",
  "arrowParens": "avoid",
  "endOfLine": "auto",
  "vueIndentScriptAndStyle": false
}
```

逐项解释为什么这么配：

| 配置 | 值 | 理由 |
|------|------|------|
| `semi` | `false` | 不写分号。Vue 社区主流风格，少敲一个键 |
| `singleQuote` | `true` | 单引号。JS 社区主流，比双引号少一个 Shift |
| `tabWidth` | `2` | 2 空格缩进。前端标配（后端 Java 常用 4） |
| `printWidth` | `100` | 每行最多 100 字符。比默认 80 宽，适配现代宽屏 |
| `trailingComma` | `none` | 不加尾逗号。和后端 Java 风格接近 |
| `arrowParens` | `avoid` | 单参数箭头函数不加括号：`x => x` 而非 `(x) => x` |
| `endOfLine` | `auto` | 自动适配操作系统换行符，避免 Windows/Linux 互相报错 |
| `vueIndentScriptAndStyle` | `false` | `<script>` / `<style>` 标签内不额外缩进 |

这些不是「唯一正确答案」，而是**团队统一**即可。Prettier 的价值不在选哪套风格，而在「机器统一」——不用 code review 时争论要不要分号、用不用单引号，格式化工具一刀切。

### 5.2 ESLint 9 Flat Config

`package.json` 里装了 ESLint 9 + `typescript-eslint` + `eslint-plugin-vue`：

```json
"devDependencies": {
  "@eslint/js": "^9.17.0",
  "eslint": "^9.17.0",
  "eslint-plugin-vue": "^9.32.0",
  "typescript-eslint": "^8.18.0"
}
```

ESLint 9 的最大变化是 **Flat Config**——废弃了旧的 `.eslintrc.*` + `.eslintignore` 两文件模式，改成一个 `eslint.config.js`（或 `.mjs`）扁平导出配置数组。

| | 旧版（.eslintrc） | ESLint 9（Flat Config） |
|------|------|------|
| 配置格式 | JSON / YAML，嵌套 extends | JS 数组，平铺 |
| 插件引用 | 字符串名 `extends: ['plugin:vue/...']` | 直接 import 插件对象 |
| 忽略文件 | 单独 `.eslintignore` | 配置里的 `ignores` 字段 |
| 继承机制 | extends 链，难追踪来源 | 数组合并，来源清晰 |

> 后端类比：旧版像 Maven 的 parent-pom 继承链，层层套；新版像 Gradle 的配置直接平铺，看一眼就知道用了什么。

目前项目把 ESLint 依赖装好了、`package.json` 里有 `lint` 脚本，但 flat config 文件还在补全中，当前主要靠 Prettier 兜住格式规范。等业务稳定后会补齐 ESLint 规则，把「未使用变量」「类型错误」等问题在开发期拦住。

### 5.3 为什么不用 husky + lint-staged

很多前端项目会在 `git commit` 前自动跑 lint（husky 注册 git hook + lint-staged 只检查暂存文件）。这个项目**没有**这么做，原因：

1. **学习项目优先简单**：husky + lint-staged 增加配置复杂度，对刚学前端的开发者，每次 commit 卡住报错反而干扰学习节奏。
2. **手动 lint 足够**：当前规模下，开发时靠编辑器实时提示 + 提交前手动 `pnpm lint && pnpm format` 就能保证质量。
3. **CI 兜底**：真正生产级项目会在 CI（GitHub Actions）里跑 lint，提交拦不住的事 CI 拦得住。本地 hook 是「锦上添花」，不是「必需品」。

> 这不代表生产环境不要 husky——团队多人协作、提交频繁时，pre-commit hook 能挡住大量低级问题。只是在当前阶段，权衡后选择手动 lint，符合「不过度工程化」的原则。

## 6. 踩坑记录

以下来自 `docs/frontend/PROGRESS.md` 的实战记录，都是真踩过的坑。

### 6.1 Element Plus 全量引入 vs 按需引入

**背景**：Element Plus 支持两种引入方式：
- 全量引入：`app.use(ElementPlus)` + 引入全量 CSS，所有组件一次注册
- 按需引入：用 `unplugin-vue-components` 的 resolver，用到哪个组件自动导入哪个

按需引入理论上包体积更小，看起来更优。但项目最终选了**全量引入**。

**踩的坑**：实测按需引入的 resolver 和全量引入同时启用时，会出现 `ERR_ABORTED` CSS 加载错误——两种机制在样式注入上冲突。

**决策**：管理后台是内部系统，不在意首屏多加载几百 KB（且 Element Plus 支持 Tree Shaking，生产构建会剔除没用到的组件）。全量引入简单可靠，避免样式冲突，是更稳妥的选择。

`main.ts` 最终写法：

```ts
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// ...
app.use(ElementPlus)
```

`vite.config.ts` 里 `unplugin-vue-components` 只用来生成组件类型声明，不带 Element Plus resolver：

```ts
Components({
  dts: 'src/components.d.ts'
  // 注意：不配 ElementPlusResolver，避免与全量引入冲突
})
```

### 6.2 pnpm Windows EPERM 权限问题

**现象**：Windows 下用 pnpm 安装依赖报 `EPERM` 权限错误。

**原因**：pnpm 默认把全局 store 放在系统盘某个路径，Windows 的权限管控可能导致读写失败。

**解决**：用本地 store 绕开，安装时指定 `--store-dir .pnpm-store`，把 store 放到项目目录下。这也是项目根目录有个 `.pnpm-store/` 文件夹的原因（应在 `.gitignore` 里忽略）。

另外注意 Node/pnpm 版本：pnpm 11 需要 Node 22+，当前环境是 Node 18.20.8 + pnpm 9.15.9。版本不匹配会直接装不上，这是 Windows 环境常见的坑。

### 6.3 Long → String 精度丢失问题

**背景**：后端 Java 的 `Long` 类型最大到 19 位，而 JavaScript 的 `Number` 只能安全表示到 `2^53 - 1`（约 16 位）。商品 ID、雪花算法生成的 ID 都是 Long，直接传给前端会精度丢失——末尾几位变成 0。

**后端方案**：Jackson 全局配置 `Long → String` 序列化，所有 Long 字段以字符串形式传给前端。

**前端影响**：看 `api/types/common.ts` 的分页响应：

```ts
export interface PageVO<T> {
  records: T[]
  total: string   // 注意：是 string，不是 number
  size: string
  current: string
  pages: string
}
```

`total`、`size` 这些分页字段也是 Long，所以类型定义成 `string`。前端用到时要 `Number()` 转换：

```ts
// 比如分页组件要的是 number，要手动转
const total = Number(pageVO.total)
```

这是全栈协作的典型场景——后端的序列化策略直接决定了前端的类型定义，两边必须对齐。

### 6.4 品牌 Logo 暂用 URL 输入的原因

**现状**：品牌管理的新增/编辑表单里，Logo 字段是「输入 URL + 图片预览」，而不是「点击上传」。

**原因**：真正的方案是 OSS 直传——前端拿 MinIO 的 Presigned URL 直接上传文件到对象存储，拿到 URL 再提交给后端。但这依赖 `mall-oss` 服务的鉴权对接，而 `mall-oss` 还没开发完。

**权衡**：为了不阻塞前端页面开发，先用 URL 输入的简化方案把表单流程跑通，等 `mall-oss` 落地后再替换成上传组件。这是「分阶段交付」的体现——核心 CRUD 流程先通，文件上传作为增强后续补。

> 这种「先用简化方案占位，后端就绪再升级」的做法在前后端并行开发时很常见，关键是**占位方案要足够简单，升级时改动范围可控**。这里 Logo 字段只在 `BrandForm.vue` 一个文件里，未来替换成 `<el-upload>` 不影响其他逻辑。

---

## 小结

这篇讲了项目基础设施的 6 个设计决策，每个都遵循同一个原则：**用最简单可靠的方式解决问题，不过度工程化**。

| 决策 | 选择了 | 而不是 | 理由 |
|------|------|------|------|
| 包管理器 | pnpm | npm/yarn | 省磁盘 + 无幽灵依赖 |
| 样式方案 | Element Plus + SCSS | Tailwind | 管理后台要统一感，不要设计自由度 |
| 组件归属 | 模块专属放 views 下 | 全堆 components | 复用范围决定位置 |
| 主题覆盖 | CSS 变量 | SCSS 重编译 | 全量引入下最省事 |
| 菜单数据 | 读路由表 | 硬编码 | 单一数据源 |
| 引入方式 | 全量引入 | 按需引入 | 避免样式冲突，简单可靠 |

基础设施搭好后，下一篇会讲通用组件（`PageTable` / `FormDialog`）和 Composables（`useTable` / `useDialog`）是怎么在这些基础设施之上封装复用能力的。
