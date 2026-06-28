# Vite 构建工具

## 1. 什么是构建工具

前端项目不能直接拿源码跑——`.ts` 文件浏览器不认、`.vue` 文件不认、`.scss` 也不认。构建工具负责把这些源码翻译、合并、压缩成浏览器能运行的产物。

对后端同学来说，构建工具就是前端的 **Maven / Gradle**：

| | Maven / Gradle | Vite |
|------|------|------|
| 作用对象 | `.java` 源码 | `.ts / .vue / .scss` 源码 |
| 核心职责 | 编译、打包、依赖管理 | 编译、打包、依赖管理 |
| 依赖声明 | `pom.xml` / `build.gradle` | `package.json` |
| 产物 | `.jar` / `.war` | `dist/` 下的 `js / css / html` |
| 开发期辅助 | —— | Dev Server + HMR 热更新 |

**前端为什么需要构建工具：**

- **语言编译**：TypeScript → JavaScript，SCSS → CSS，Vue SFC → JavaScript 模块
- **模块合并**：一个项目几百个文件，浏览器逐个请求太慢，需要打包成少量文件
- **开发体验**：本地起一个开发服务器，改代码后页面自动刷新（HMR）
- **生产优化**：代码压缩、未使用代码剔除（Tree Shaking）、按需加载

## 2. Webpack vs Vite

Webpack 是上一代主流构建工具，Vite 是新一代。两者的核心差异在**开发期的启动策略**。

### Webpack：先打包再启动

开发服务器启动时，Webpack 会从入口开始，把所有模块递归编译打包成一个 bundle，然后才启动服务。

```
启动 Webpack Dev Server：
  入口 → 递归编译所有依赖 → 生成 bundle → 启动服务
  （项目越大，启动越慢，几分钟很常见）
```

修改某个文件后，HMR 需要重新编译该模块及其所有依赖链，速度随项目规模下降。

### Vite：开发期用原生 ESM，按需编译

Vite 利用现代浏览器原生支持的 **ES Module**（`<script type="module">`），开发期**不打包**，让浏览器直接按需请求每个模块。Vite 只做一件事：拦截请求，把 `.ts / .vue / .scss` 实时编译成浏览器能跑的 JS / CSS。

```
启动 Vite Dev Server：
  直接启动服务（几乎瞬间）
  浏览器请求 main.ts → Vite 实时编译返回
  浏览器请求 A.vue → Vite 实时编译返回
  （只编译当前页面用到的模块，用多少编多少）
```

| | Webpack | Vite |
|------|------|------|
| 开发启动速度 | 慢（全量打包） | 快（不打包，按需编译） |
| HMR 速度 | 随项目变大而变慢 | 始终快（只编译改动模块） |
| 开发期产物 | 打包后的 bundle | 原生 ESM 模块 |
| 生产构建 | Webpack 自身打包 | Rollup 打包 |
| 配置复杂度 | 高 | 低 |

### Vite 的双模式原理

```
开发期（vite dev）：                    生产构建（vite build）：
  浏览器 ──原生 ESM──► Vite Dev Server    源码 ──Rollup──► dist/
  按需编译，不打包                        打包、压缩、Tree Shaking
  （快）                                 （产物小、加载快）
```

- **开发期**：用原生 ESM，浏览器直接跑模块，Vite 按需编译，所以启动快、HMR 快
- **生产构建**：用 Rollup 打包，做 Tree Shaking、代码压缩、按需加载，保证产物性能

### 补充：依赖预构建（esbuild）

`node_modules` 里的第三方包大多是 CommonJS 格式，浏览器原生 ESM 不认。Vite 启动时会用 **esbuild**（Go 写的极速打包器）把依赖预打包成 ESM 格式：

```
启动时：
  node_modules 里的依赖 ──esbuild 预打包──► node_modules/.vite/deps/（ESM 格式）
                                              ↑
                                  浏览器请求时直接返回缓存
```

这一步只对第三方依赖做一次，之后缓存复用，所以不影响开发体验。源码（`src/` 下）则始终按需编译，不预打包。

## 3. Vite 配置

项目的完整配置在 `mall-admin-frontend/vite.config.ts`：

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import path from 'path'

// defineConfig 提供类型提示，写错配置 IDE 会报红
export default defineConfig({
  plugins: [
    // ① 解析 .vue 单文件组件（SFC）
    vue(),
    // ② 自动导入 Vue / Vue Router / Pinia 的 API（ref, reactive, useRouter 等）
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts'
    }),
    // ③ 自动注册组件（项目用全量引入 Element Plus，这里只生成类型，不配 resolver）
    Components({
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')  // @ → src 目录
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        // ④ 全局注入 SCSS 变量，所有组件可直接使用 $color-primary 等
        additionalData: `@use "@/assets/styles/variables.scss" as *;`
      }
    }
  },
  server: {
    port: 5173,  // 与网关 CORS 配置一致
    open: true   // 启动后自动打开浏览器
  }
})
```

### 插件机制

Vite 插件类似 Maven 的插件——扩展构建管线的能力。上面用到的插件：

| 插件 | 作用 | 类比 |
|------|------|------|
| `@vitejs/plugin-vue` | 让 Vite 能编译 `.vue` 文件 | 编译器插件 |
| `unplugin-auto-import` | 自动导入 Vue/Router/Pinia API | —— |
| `unplugin-vue-components` | 自动注册组件 | —— |

### 路径别名

`resolve.alias` 把 `@` 映射到 `src` 目录，import 时不用写相对路径：

```typescript
// ❌ 相对路径，层级深时容易写错
import { getUsers } from '../../../api/system/user'

// ✅ 别名，从 src 开始算
import { getUsers } from '@/api/system/user'
```

注意：别名要在 `vite.config.ts` 和 `tsconfig.app.json` 两处都配，前者让 Vite 认识，后者让 TypeScript 认识（否则 IDE 报红）。

## 4. 自动导入（项目特色）

### unplugin-auto-import：API 自动导入

传统写法每个文件都要手动 import：

```typescript
// ❌ 每个 .vue / .ts 文件都要写一堆 import
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useStore } from 'pinia'

const count = ref(0)
const router = useRouter()
```

配置了 `AutoImport` 后，这些 API 直接用，不用写 import：

```vue
<script setup lang="ts">
// ✅ 直接用，不用 import
const count = ref(0)              // ref 自动来自 vue
const router = useRouter()        // useRouter 自动来自 vue-router
const store = useCartStore()      // useCartStore 来自 pinia（defineStore 也自动导入）
</script>
```

插件在编译时**自动分析代码**，发现用了 `ref` 就在文件顶部注入 `import { ref } from 'vue'`，开发者无感知。

### unplugin-vue-components：组件自动注册

传统写法要用一个组件得手动 import + 注册：

```typescript
// ❌ 繁琐
import PageTable from '@/components/PageTable/index.vue'
import FormDialog from '@/components/FormDialog/index.vue'
```

配置了 `Components` 后，`src/components` 下的组件可以直接在模板里用，插件自动注入 import。

> **注意**：本项目 Element Plus 采用**全量引入**（在 `main.ts` 中 `app.use(ElementPlus)`），不使用按需引入 resolver，目的是避免样式冲突。所以 `Components` 插件这里只负责项目自有组件的自动注册和类型生成。

### 补充：Element Plus 全量引入

来看 `src/main.ts` 的实际写法：

```typescript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'                // 全量引入组件库
import 'element-plus/dist/index.css'                  // 全量引入样式
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import '@/assets/styles/index.scss'

const app = createApp(App)

// 注册 Element Plus 图标为全局组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)   // 注册全部组件
app.mount('#app')
```

**全量引入 vs 按需引入：**

| | 全量引入（本项目） | 按需引入 |
|------|------|------|
| 写法 | `app.use(ElementPlus)` 一次到位 | 配 resolver，用哪个引哪个 |
| 产物体积 | 较大（全量 CSS/JS） | 较小 |
| 样式一致性 | ✅ 统一，无冲突 | 可能出现样式覆盖问题 |

学习项目优先保证稳定性和一致性，所以选全量引入。生产项目对体积敏感时可改按需引入。

### 生成的 dts 类型声明文件

两个插件会生成类型声明文件：

- `src/auto-imports.d.ts`：声明 `ref`、`useRouter` 等是全局可用的，让 TypeScript 不报错
- `src/components.d.ts`：声明自动注册的组件类型

```typescript
// src/auto-imports.d.ts（自动生成，不要手动编辑）
// Generated by 'unplugin-auto-import'
declare global {
  const ref: typeof import('vue')['ref']
  const computed: typeof import('vue')['computed']
  const useRouter: typeof import('vue-router')['useRouter']
  // ...
}
```

这些文件需要被 `tsconfig.app.json` 的 `include` 包含（本项目用 `src/**/*.d.ts` 通配），否则 IDE 找不到类型。

### 优点和注意事项

**优点：**

- 减少样板代码，写起来更专注业务逻辑
- 不用记 API 来自哪个包

**注意事项：**

- 生成的 `*.d.ts` 是构建产物，**不要手动编辑**，删掉后会自动重新生成
- 滥用全局自动导入会让"变量从哪来"不直观，团队需约定哪些该自动导入、哪些该显式 import

## 5. SCSS 全局注入

### 配置

```typescript
css: {
  preprocessorOptions: {
    scss: {
      additionalData: `@use "@/assets/styles/variables.scss" as *;`
    }
  }
}
```

`additionalData` 的作用：在**每个 SCSS 文件编译前**，自动在顶部注入这行代码。

### 效果

项目的 `variables.scss` 定义了一堆变量：

```scss
// src/assets/styles/variables.scss
$color-primary: #409eff;
$color-success: #67c23a;
$sidebar-width: 210px;
// ...
```

配置了 `additionalData` 后，在任何 `.vue` 的 `<style lang="scss">` 里都能直接用这些变量，不用手动 import：

```vue
<!-- 任意组件 -->
<style scoped lang="scss">
.header {
  color: $color-primary;        // ✅ 直接用，不用 @use
  width: $sidebar-width;
}
</style>
```

### 原理

```
你写的 SCSS:              Vite 实际编译的 SCSS:
─────────────             ────────────────────────────────
.header {                 @use "@/assets/styles/variables.scss" as *;  ← 自动注入
  color: $color-primary;  .header {
}                           color: $color-primary;
                          }
```

Vite 的 SCSS 预处理器在编译每个 SCSS 文件前，会把 `additionalData` 的内容拼接到文件最前面。等价于每个文件都写了 `@use "..." as *;`，但不用开发者重复写。

## 6. 环境变量

### 多环境配置文件

项目有两个环境文件：

```bash
# .env.development（开发环境）
VITE_API_BASE_URL=http://localhost:1000/api

# .env.production（生产环境）
VITE_API_BASE_URL=/api
```

Vite 根据 `--mode` 加载对应的文件：`vite`（开发）加载 `.env.development`，`vite build`（生产）加载 `.env.production`。

### 命名规则

只有以 **`VITE_`** 开头的变量才会暴露给前端代码（安全考虑，避免把数据库密码等敏感配置泄露到浏览器）。

### 读取方式

通过 `import.meta.env` 读取：

```typescript
// src/utils/request.ts
const baseConfig = {
  baseURL: import.meta.env.VITE_API_BASE_URL,  // 读环境变量
  timeout: 10000
}
```

### 类型声明

`env.d.ts` 给环境变量加类型，让 IDE 有提示：

```typescript
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
```

这样写 `import.meta.env.VITE_API_BASE_URL` 时，IDE 知道是 `string` 类型，写错变量名会报红。

## 7. TypeScript 集成

### tsconfig 三文件体系

项目用了 TypeScript 的 **Project References**（项目引用），把配置拆成三个文件：

```
tsconfig.json        ← 根配置，自身不编译，只引用下面两个
├── tsconfig.app.json    ← 管理应用代码（src 下的 .ts / .vue）
└── tsconfig.node.json   ← 管理构建脚本（vite.config.ts）
```

**根配置** `tsconfig.json`——只做引用，不直接编译文件：

```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

**应用配置** `tsconfig.app.json`——管理跑在浏览器里的代码：

```json
{
  "extends": "@vue/tsconfig/tsconfig.dom.json",   // 继承 Vue 官方 DOM 配置
  "compilerOptions": {
    "composite": true,                            // 启用增量编译（Project References 必需）
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "baseUrl": ".",
    "paths": { "@/*": ["src/*"] },                // 路径别名，与 vite.config.ts 一致
    "types": ["vite/client"]                       // 引入 import.meta.env 等类型
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue", "src/**/*.d.ts"]
}
```

**Node 配置** `tsconfig.node.json`——管理跑在 Node 环境的构建脚本：

```json
{
  "compilerOptions": {
    "composite": true,
    "target": "ES2022",
    "lib": ["ES2023"],         // Node 环境，不需要 DOM 类型
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true
  },
  "include": ["vite.config.ts"]   // 只包含构建配置
}
```

**为什么要拆分？** 因为应用代码跑在浏览器（需要 DOM 类型库），构建脚本跑在 Node（需要 Node 类型），运行环境不同，类型库配置也不同。拆开后各自独立类型检查，互不干扰，还能增量编译提速度。

### vue-tsc 类型检查

`tsc` 是 TypeScript 官方编译器，但它不认识 `.vue` 文件。`vue-tsc` 是 Vue 官方的类型检查工具，能解析 `.vue` SFC 并做类型检查。

`package.json` 里的 build 脚本：

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview"
  }
}
```

- `vue-tsc -b`：`-b` 是 build 模式，按 Project References 增量类型检查，先确保代码无类型错误
- `&&`：类型检查通过后才执行构建
- `vite build`：用 Rollup 打包生成生产产物

> 类比 Maven：`vue-tsc -b` 相当于 `mvn compile`（编译检查），`vite build` 相当于 `mvn package`（打包）。

## 8. 开发与构建命令

| 命令 | 作用 | 类比 |
|------|------|------|
| `pnpm dev` | 启动开发服务器（HMR 热更新） | `mvn spring-boot:run` |
| `pnpm build` | 类型检查 + 生产构建，产物在 `dist/` | `mvn package` |
| `pnpm preview` | 本地预览构建产物 | 跑 jar 看效果 |

### pnpm dev

```bash
pnpm dev   # 实际执行 vite
```

启动 Vite 开发服务器，监听 `5173` 端口。改代码后浏览器**局部热更新**（只替换改动的组件，不刷新整页），开发体验流畅。

### pnpm build

```bash
pnpm build   # 实际执行 vue-tsc -b && vite build
```

分两步：

1. `vue-tsc -b`：全项目类型检查，有类型错误直接报错中断（生产级要求，不让类型有问题的代码上线）
2. `vite build`：Rollup 打包，产物输出到 `dist/`，包含压缩后的 JS / CSS / HTML

构建产物结构大致如下：

```
dist/
├── index.html              # 入口 HTML（注入了打包后的 JS/CSS 引用）
├── assets/
│   ├── index-[hash].js     # 业务代码（含 Vue、组件等）
│   ├── index-[hash].css    # 样式（含 Element Plus、SCSS 编译结果）
│   └── ...                 # 图片、字体等静态资源
```

> 文件名带 `[hash]` 是为了缓存失效——内容变了 hash 就变，浏览器会重新下载，避免用到旧缓存。

### pnpm preview

```bash
pnpm preview   # 实际执行 vite preview
```

起一个静态服务器托管 `dist/` 产物，用于验证构建结果是否正常（开发期用的是未打包的 ESM，和打包后的产物可能有差异）。

### 补充：package.json 的 "type": "module"

```json
{
  "type": "module"
}
```

这一行声明项目使用 **ESM** 模块规范（`import/export`），而不是 Node 传统的 CommonJS（`require/module.exports`）。Vite 默认基于 ESM 工作，所有配置文件（`vite.config.ts`）和源码都用 `import` 语法，所以必须声明 `"type": "module"`。

## 9. 小结

| 概念 | 一句话理解 |
|------|------|
| 构建工具 | 前端的 Maven，负责编译 TS/Vue/SCSS + 打包 |
| Vite 开发模式 | 用浏览器原生 ESM，按需编译，启动飞快 |
| Vite 生产模式 | 用 Rollup 打包压缩，产物小 |
| vite.config.ts | 构建配置中枢，配插件 / 别名 / 样式 / 服务器 |
| 自动导入 | 插件帮你写 import，减少样板代码 |
| SCSS 全局注入 | 每个文件自动注入变量文件，全局用 $变量 |
| 环境变量 | `VITE_` 前缀的变量通过 `import.meta.env` 读取 |
| Project References | 拆分 tsconfig，应用代码和构建脚本分开类型检查 |
| vue-tsc -b && vite build | 先类型检查，再打包，生产级标准 |
