# 前端开发

> 面向有后端开发经验但不熟悉前端的开发者，从 Vue 3 框架基础到项目实战，逐步掌握 my-mall 商城前端开发

## 📚 文档目录

| 文档 | 内容 | 适合阶段 |
|------|------|----------|
| [01-Vue3 基础](./01-vue3-basics.md) | Composition API、响应式原理、组件化、生命周期 | 入门 |
| [02-Vue Router](./02-vue-router.md) | 路由配置、懒加载、导航守卫 | 入门 |
| [03-Pinia 状态管理](./03-pinia-state.md) | Store 定义、持久化、使用场景判断 | 入门 |
| [04-TypeScript 实践](./04-typescript-essentials.md) | 类型注解、接口、泛型在 Vue 中的应用 | 入门 |
| [05-Element Plus](./05-element-plus.md) | 布局组件、表单组件、主题定制 | 入门 |
| [06-Vite 构建](./06-vite-build.md) | 配置、别名、SCSS 全局注入、自动导入 | 进阶 |
| [07-基础设施设计](./07-infrastructure.md) | 对照项目代码讲解设计思路与取舍 | 实战 |
| [08-通用组件与 Composables](./08-composables-components.md) | PageTable/FormDialog/useTable 设计 | 实战 |
| [09-API 层设计](./09-api-layer.md) | Axios 封装、拦截器、R&lt;T&gt; 剥离、类型定义 | 实战 |
| [10-功能页面实现](./10-feature-pages.md) | 分类树拖拽、品牌关联分类弹窗等 | 实战 |

## 🎯 学习路径

```
┌─────────────────────────── 入门篇 ───────────────────────────┐
│                                                               │
│  Vue3 基础 ──► Vue Router ──► Pinia ──► TypeScript ──► Element Plus
│                                                               │
└─────────────────────────────────┬─────────────────────────────┘
                                  │
                                  ▼
                      ┌──── 进阶篇 ────┐
                      │                 │
                      │   Vite 构建     │
                      │                 │
                      └────────┬────────┘
                               │
                               ▼
┌─────────────────────────── 实战篇 ───────────────────────────┐
│                                                               │
│  基础设施设计 ──► 通用组件/Composables ──► API 层设计 ──► 功能页面
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

## 📋 核心知识点

### 1. Vue 3 框架基础
- Composition API vs Options API
- 响应式原理（ref / reactive / computed / watch）
- 组件化思想（props / emits / slots / provide-inject）
- 生命周期钩子
- 单文件组件（SFC）结构

### 2. 路由与状态管理
- Vue Router 路由配置与动态路由
- 路由懒加载与代码分割
- 导航守卫（鉴权、进度条）
- Pinia Store 定义与使用
- 状态持久化方案
- 何时不该用全局状态

### 3. TypeScript 实践
- 类型注解与类型推断
- 接口（interface）与类型别名（type）
- 泛型在 API 封装中的应用
- Vue 组件的类型定义（defineProps / defineEmits）
- 与后端 DTO 的类型对应

### 4. UI 组件库
- Element Plus 布局组件（Container / Layout）
- 表单组件与校验规则
- 表格组件与分页
- 主题定制（CSS 变量、SCSS 覆盖）
- 按需引入与自动导入

### 5. 构建工具与工程化
- Vite 配置（别名、代理、环境变量）
- SCSS 全局变量注入
- 组件与 API 自动导入（unplugin-auto-import / unplugin-vue-components）
- 生产构建优化
- ESLint / Prettier 配置

### 6. 项目实战设计
- 前端基础设施分层（request / utils / composables / components）
- 通用组件设计模式（PageTable / FormDialog）
- Composables 复用逻辑（useTable / useDialog）
- API 层封装（Axios 拦截器、统一错误处理、R&lt;T&gt; 剥离）
- 后端协作约定（统一返回结构、错误码、分页协议）

## 🛠 技术栈

| 技术 | 用途 |
|------|------|
| Vue 3 | 前端框架（Composition API） |
| TypeScript | 类型系统 |
| Vite | 构建工具与开发服务器 |
| Element Plus | UI 组件库 |
| Pinia | 状态管理 |
| Vue Router | 路由管理 |
| Axios | HTTP 客户端 |
| SCSS | 样式预处理 |

## 🔗 相关资源

- [Vue 3 官方文档](https://cn.vuejs.org/)
- [Vue Router](https://router.vuejs.org/zh/)
- [Pinia](https://pinia.vuejs.org/zh/)
- [Element Plus](https://element-plus.org/zh-CN/)
- [Vite](https://cn.vitejs.dev/)
- [TypeScript](https://www.typescriptlang.org/zh/)
