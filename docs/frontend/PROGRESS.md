# 前端 进度

> 本文档只记录**已完成**和**当前进行**的事项，不列未来计划。
> 完成一个事项后，将过程中多个小提交合并为一条提交并推送，在此记录关联提交 hash。

---

## 已完成

| 时间 | 事项 | 关联提交 | 说明 |
|------|------|---------|------|
| 2026-06-28 | 管理后台前端基础设施搭建 + 分类/品牌管理页面 | — | Vite + Vue 3 + TS + Element Plus + Pinia + Axios 脚手架；SCSS 样式系统（变量/Reset/主题定制/工具类）；API 请求层（Axios 拦截器剥离 R\<T\> + 统一错误处理）；路由系统（懒加载 + meta + afterEach 标题）；AdminLayout 布局（侧边栏可折叠 + 顶栏 + 面包屑 + 内容区过渡）；通用组件 PageTable + FormDialog；Composables useTable + useDialog；树形工具函数；分类管理（三级树 CRUD + 拖拽排序 + 批量删除）；品牌管理（分页 CRUD + 显示状态切换 + 关联分类管理）；404 页面 |
| 2026-06-29 | 前端联调验证与 PageTable 刷新修复 | — | 开发服务器启动并对接 mall-gateway（CORS 允许 5173~5176）；分类/品牌管理页面端到端验证通过；修复品牌新增/删除后 PageTable 列表未自动刷新的问题（brand/index.vue 移除冗余 useTable，改用 PageTable ref 暴露的 refresh / tableData / pageNum） |
| 2026-06-29 | 前端文档体系补充与学习文档 | — | 修正 overview.md 与实现对齐（目录结构/ID 类型/路由示例）；新增 infrastructure.md（基础设施设计，含待实现组件规划）、components.md（通用组件与 composables 速查）、coding-standards.md（编码规范）；调整 design-system.md 通用组件章节职责；功能域文档章节顺序调整为「需求→前端设计→后端实现」（doc-convention.md 规范 + category/brand-management.md 同步）；新增 learn-docs/frontend/ 10 篇学习文档（Vue3 基础/路由/Pinia/TS/Element Plus/Vite/基础设施设计/通用组件/API 层/功能页面实现） |

## 当前进行

| 事项 | 说明 |
|------|------|
| | |

---

## 关键记录

> 记录开发过程中的重要决策、踩坑、技术选型理由等。

### 2026-06-28 前端基础设施搭建

- **技术选型**：Vue 3 + TypeScript + Vite 6 + Element Plus + Pinia + Axios，与 `docs/frontend/overview.md` 定义一致
- **不使用 Tailwind**：项目 UI 统一 Element Plus + SCSS 变量系统，保持企业级管理后台一致性
- **Element Plus 引入方式**：全量引入（main.ts 中 `app.use(ElementPlus)`），不使用按需 resolver，避免样式冲突（实测按需引入 + 全量引入同时使用会导致 ERR_ABORTED CSS 加载错误）
- **API 请求层**：响应拦截器自动剥离 `R<T>` 外壳，业务错误弹 ElMessage，网络错误弹 ElNotification
- **pnpm store 问题**：Windows 下 pnpm 默认 store 路径有 EPERM 权限问题，使用 `--store-dir .pnpm-store` 本地 store 解决
- **Node/pnpm 版本**：Node 18.20.8 + pnpm 9.15.9（pnpm 11 需 Node 22+，当前环境不支持）
- **Logo 上传**：本期品牌 Logo 使用 URL 输入 + 图片预览方案，OSS 直传待 mall-oss 鉴权对接后实现
- **鉴权/登录**：暂不实现，打开即进入分类管理页，后续 mall-auth 落地后补充路由守卫 + token 管理

### 2026-06-29 联调验证

- **mall-gateway CORS**：Vite 开发服务器端口会被动态占用（5173/5174/5175/5176），网关 `allowed-origins` 已扩展为 5173~5176，避免前端换端口后跨域失败
- **品牌新增后列表未刷新**：brand/index.vue 原先同时使用了 `useTable` 和 `<PageTable :fetch="...">`，导致父组件 `refresh()` 实际刷新的是 `useTable` 的独立数据，页面上看到的表格未更新。修复方案：移除 `useTable`，父组件通过 `PageTable` ref 调用 `refresh()`，并读取 `tableData`/`pageNum` 处理删除后分页回退
- **分类新增后弹窗关闭**：CategoryForm 提交成功后先 `visible.value = false` 再 `emit('success')`，确保弹窗关闭与父组件树刷新顺序正确
