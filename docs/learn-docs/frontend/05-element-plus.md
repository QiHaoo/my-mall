# Element Plus 组件库

> 面向有 Java/Spring 后端经验、但不熟悉前端的开发者。重点讲清项目实际用到的组件与用法。
> 文中代码示例多数取自本项目的 `mall-admin-frontend/src/`，可直接对照源码阅读。

## 1. 什么是 Element Plus

Element Plus 是 Vue 3 生态中最成熟的企业级 UI 组件库，提供按钮、表单、表格、弹窗、树等 80+ 开箱即用的组件。

### 1.1 后端类比

| | 后端世界 | 前端世界 |
|------|------|------|
| 类比 | Bootstrap / Foundation | Element Plus / Ant Design Vue |
| 区别 | 纯 CSS + JS，需手动操作 DOM | 与 Vue 深度集成，响应式数据驱动 |
| 价值 | 不用从零写样式 | 不用从零写样式，且不用手写交互逻辑 |

> 简单说：Element Plus 之于前端，就像 Spring Data JPA 之于后端——把重复的样板活儿封装好，让你专注业务。

### 1.2 为什么选 Element Plus

| 维度 | 说明 |
|------|------|
| 生态成熟 | 饿了么团队维护，GitHub 25k+ star，Vue 3 组件库事实标准 |
| 文档完善 | 官方中文文档齐全，每个组件都有可运行示例 |
| 中文友好 | 国内使用率最高，遇到问题搜索中文社区即可解决 |
| TypeScript | 全量类型定义，IDE 智能提示友好 |

## 2. 引入方式

### 2.1 全量引入（项目采用）

一次性注册所有组件，简单直接：

```ts
// mall-admin-frontend/src/main.ts
import { createApp } from 'vue'
import ElementPlus from 'element-plus'          // 组件逻辑
import 'element-plus/dist/index.css'             // 全量样式
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const app = createApp(App)

// 注册 Element Plus 图标为全局组件（图标是独立包，需手动注册）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus)  // 注册所有组件
app.mount('#app')
```

注册后，在模板中可直接用组件名引用图标：`<el-icon><Plus /></el-icon>`。

### 2.2 按需引入（resolver）

借助 `unplugin-auto-import` 和 `unplugin-vue-components`，只在用到组件时自动导入：

```ts
// vite.config.ts
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default {
  plugins: [
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] }),
  ],
}
```

### 2.3 两种方式对比

| | 全量引入 | 按需引入 |
|------|------|------|
| 打包体积 | 较大（约 600KB gzip） | 较小（只打包用到的） |
| 配置复杂度 | 低，一行 `app.use()` | 高，需配置 resolver |
| 样式管理 | 全量 CSS，一致性好 | 按需加载，可能出现样式遗漏 |
| 适合场景 | 中后台管理系统 | C 端应用、对体积敏感 |

**为什么项目选全量引入**：后台管理系统组件使用密集，对首屏体积不敏感。全量引入能避免按需+全量混用导致的样式覆盖不全问题，开发效率优先。

## 3. 常用组件分类讲解

### 3.1 布局组件

`el-container` / `el-aside` / `el-header` / `el-main` 用于搭建页面骨架：侧边栏 + 导航栏 + 内容区。

项目 `AdminLayout.vue` 没有直接用 `el-container`，而是用 flex 布局自行实现，但结构等价：

```vue
<!-- mall-admin-frontend/src/layouts/AdminLayout.vue -->
<template>
  <div class="admin-layout">
    <Sidebar />           <!-- 等价 el-aside -->
    <div class="main-container">
      <Navbar />          <!-- 等价 el-header -->
      <AppMain />         <!-- 等价 el-main -->
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin-layout { display: flex; height: 100%; }
.main-container { flex: 1; display: flex; flex-direction: column; }
</style>
```

> **后端类比**：布局组件就像模板引擎的 layout 继承——把公共骨架抽出来，每个页面只填内容区。

### 3.2 表单组件

`el-form` / `el-form-item` / `el-input` / `el-select` 是后台系统最高频的组件。项目 `PageTable` 的搜索栏用了内联表单（`inline`）：

```vue
<!-- mall-admin-frontend/src/components/PageTable/index.vue -->
<el-form :inline="true" :model="searchParams">
  <el-form-item v-for="field in searchFields" :key="field.prop" :label="field.label">
    <el-input
      v-if="field.component === 'input'"
      v-model="searchParams[field.prop]"
      clearable
      @keyup.enter="handleSearch"
    />
    <el-select v-else-if="field.component === 'select'" v-model="searchParams[field.prop]" clearable>
      <el-option v-for="opt in field.options" :key="opt.value" :label="opt.label" :value="opt.value" />
    </el-select>
  </el-form-item>
  <el-form-item>
    <el-button type="primary" @click="handleSearch">查询</el-button>
    <el-button @click="handleReset">重置</el-button>
  </el-form-item>
</el-form>
```

| 属性 | 作用 |
|------|------|
| `:inline="true"` | 表单项横向排列（搜索栏常用） |
| `v-model` | 双向绑定，类似后端 DTO 的字段映射 |
| `clearable` | 显示清除按钮 |
| `el-option` | 下拉选项，`label` 显示文本，`value` 实际值 |

### 3.3 数据展示组件

`el-table` + `el-pagination` 是后台列表页的标配：

```vue
<!-- mall-admin-frontend/src/components/PageTable/index.vue -->
<el-table ref="tableRef" v-loading="loading" :data="tableData" stripe border>
  <el-table-column v-if="selectable" type="selection" width="55" />
  <el-table-column
    v-for="col in columns"
    :key="col.prop"
    :prop="col.prop"
    :label="col.label"
    show-overflow-tooltip
  >
    <!-- 通过插槽自定义列内容 -->
    <template v-if="col.slot" #default="scope">
      <slot :name="`col-${col.prop}`" :row="scope.row" :index="scope.$index" />
    </template>
  </el-table-column>
</el-table>

<el-pagination
  v-model:current-page="pageNum"
  v-model:page-size="pageSize"
  :total="total"
  :page-sizes="[10, 20, 50]"
  layout="total, sizes, prev, pager, next, jumper"
  background
/>
```

| 属性 | 作用 |
|------|------|
| `v-loading` | 加载遮罩 |
| `stripe` / `border` | 斑马纹 / 边框 |
| `show-overflow-tooltip` | 内容过长显示省略号，hover 显示完整 |
| `#default="scope"` | 自定义单元格内容（插槽） |
| `v-model:current-page` | 当前页码（Vue 3 多 v-model 语法） |

> **后端类比**：`el-table` 就像 MyBatis-Plus 的 `IPage` 分页结果——你给数据，它负责渲染。`el-pagination` 对应后端的分页参数（pageNum / pageSize）。

### 3.4 反馈组件

`el-dialog` / `el-message` / `el-message-box` / `el-notification` 用于用户交互反馈。

**el-dialog（弹窗）**——项目 `FormDialog` 的核心：

```vue
<!-- mall-admin-frontend/src/components/FormDialog/index.vue -->
<el-dialog
  v-model="dialogVisible"
  :title="title"
  :close-on-click-modal="false"   <!-- 点击遮罩不关闭 -->
  destroy-on-close                 <!-- 关闭时销毁内容 -->
>
  <el-form ref="formRef" :model="formData" :rules="rules">
    <slot :form-model="formData" :form-ref="formRef" />
  </el-form>
  <template #footer>
    <el-button @click="handleCancel">取消</el-button>
    <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
  </template>
</el-dialog>
```

**el-message / el-message-box**——分类管理删除确认：

```ts
// mall-admin-frontend/src/views/product/category/index.vue
await ElMessageBox.confirm(
  `确定删除分类「${data.name}」及其所有子分类吗？`,
  '删除确认',
  { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
)
await batchDeleteCategories([data.catId])
ElMessage.success('删除成功')   // 轻量提示
```

四种反馈组件的区别：

| 组件 | 用途 | 类比后端 |
|------|------|------|
| `el-message` | 轻量提示（自动消失） | `logger.info()` |
| `el-message-box` | 确认对话框（阻塞式） | `Scanner.nextLine()` 等待用户输入 |
| `el-notification` | 通知（右上角，可手动关闭） | 持久化的日志通知 |
| `el-dialog` | 内容弹窗（表单/详情） | 模态窗口 |

### 3.5 导航组件

**el-menu（侧边栏菜单）**：

```vue
<!-- mall-admin-frontend/src/layouts/components/Sidebar.vue -->
<el-menu
  :default-active="activeMenu"
  :collapse="appStore.sidebarCollapsed"
  background-color="#304156"
  text-color="#bfcbd9"
  active-text-color="#409eff"
  @select="handleSelect"
>
  <el-menu-item v-for="item in menuItems" :key="item.path" :index="'/' + item.path">
    <el-icon v-if="item.meta?.icon">
      <component :is="iconMap[item.meta.icon]" />
    </el-icon>
    <template #title>{{ item.meta?.title }}</template>
  </el-menu-item>
</el-menu>
```

| 属性 | 作用 |
|------|------|
| `:default-active` | 当前激活菜单项（高亮） |
| `:collapse` | 是否折叠（侧边栏收起） |
| `@select` | 选中菜单触发，`index` 即路由路径 |
| `<component :is>` | 动态组件，根据图标名渲染对应图标 |

**el-breadcrumb（面包屑）**：

```vue
<!-- mall-admin-frontend/src/layouts/components/Breadcrumb.vue -->
<el-breadcrumb separator="/">
  <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
    {{ item.meta.title }}
  </el-breadcrumb-item>
</el-breadcrumb>
```

### 3.6 其他常用组件

**el-button**——通过 `type` 区分样式：`primary`（主要）、`danger`（危险）、`success`（成功），`loading` 属性显示加载态。

**el-switch**——开关，分类管理用于开启拖拽：`<el-switch v-model="dragEnabled" />`。

**el-tree-select**——树形选择器，品牌关联分类用：

```vue
<!-- mall-admin-frontend/src/views/product/brand/components/BrandRelationDialog.vue -->
<el-tree-select
  v-model="selectedCatelogId"
  :data="categoryTree"
  :props="{
    label: 'name',
    value: 'catId',
    children: 'children',
    disabled: (data) => data.catLevel !== 3   <!-- 只有三级分类可选 -->
  }"
  check-strictly
  placeholder="请选择三级分类"
/>
```

## 4. 主题定制

Element Plus 的样式基于 CSS 变量，覆盖变量即可定制主题。

### 4.1 CSS 变量覆盖（项目做法）

```scss
// mall-admin-frontend/src/assets/styles/element-plus.scss
:root {
  --el-color-primary: #409eff;    // 主题色
  --el-color-success: #67c23a;    // 成功色
  --el-color-warning: #e6a23c;    // 警告色
  --el-color-danger: #f56c6c;     // 危险色
  --el-border-radius-base: 4px;   // 圆角
}
```

> 所有 Element Plus 组件内部都引用这些变量，改一处全局生效。

### 4.2 SCSS 变量（项目自有变量）

项目还维护了一套自有 SCSS 变量，用于自定义业务样式：

```scss
// mall-admin-frontend/src/assets/styles/variables.scss
$color-primary: #409eff;       // 与 Element Plus 主题色保持一致
$sidebar-bg: #304156;          // 侧边栏背景
$sidebar-width: 210px;         // 侧边栏宽度
$font-size-base: 14px;         // 基础字号
```

> **CSS 变量 vs SCSS 变量**：CSS 变量运行时生效（浏览器解析），SCSS 变量编译时生效（打包后固定）。改 Element Plus 用 CSS 变量，写业务样式用 SCSS 变量。

### 4.3 为什么不用运行时动态主题

Element Plus 支持运行时切换主题色（通过 JS 动态生成 CSS），但存在 **FOUC（Flash of Unstyled Content）** 问题——页面加载瞬间会闪烁旧主题色。

后台管理系统主题固定，不需要动态切换，因此项目选择编译时通过 CSS 变量覆盖，简单且无闪烁。

## 5. 表单校验

### 5.1 rules 定义

校验规则通过 `rules` 属性传入，每个字段对应一组规则：

```ts
import type { FormRules } from 'element-plus'

const rules: FormRules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 2, max: 20, message: '长度 2-20 个字符', trigger: 'blur' },
  ],
  catLevel: [
    { required: true, message: '请选择层级', trigger: 'change' },
  ],
}
```

| 规则 | 说明 |
|------|------|
| `required` | 必填 |
| `min` / `max` | 最小/最大长度 |
| `pattern` | 正则校验 |
| `validator` | 自定义校验函数 |
| `trigger` | 触发时机：`blur`（失焦）/ `change`（值变化） |

### 5.2 validate() / validateField()

```ts
const formRef = ref<FormInstance>()

await formRef.value.validate()           // 校验整个表单（失败会 reject）
await formRef.value.validateField('name') // 校验单个字段
formRef.value.clearValidate()             // 清除校验状态
```

### 5.3 项目实例：FormDialog 校验逻辑

```ts
// mall-admin-frontend/src/components/FormDialog/index.vue
async function handleSubmit() {
  if (!formRef.value) return
  try {
    if (props.rules) {
      await formRef.value.validate()   // 校验失败会抛异常
    }
    submitting.value = true
    await props.submit(formData.value) // 校验通过后提交
    emit('success')
    dialogVisible.value = false
  } catch (err) {
    // 校验失败或提交失败，不关闭弹窗
  } finally {
    submitting.value = false
  }
}
```

弹窗打开时重置校验状态（避免显示上次的红字）：

```ts
watch(() => props.modelValue, async (val) => {
  if (val) {
    formData.value = { ...props.initialData }
    await nextTick()                 // 等 DOM 更新完
    formRef.value?.clearValidate()   // 清除上次的校验红字
  }
})
```

> **后端类比**：表单校验就像 Spring 的 `@Valid` + Bean Validation。`rules` 对应 `@NotBlank` / `@Size` 注解，`validate()` 对应 `@Valid` 触发校验。区别是前端校验即时反馈，后端校验兜底。

## 6. el-tree 深入

`el-tree` 是分类管理的核心组件，支持树形数据展示、拖拽排序、复选框等。

### 6.1 数据结构（children 嵌套）

```ts
interface CategoryVO {
  catId: string
  name: string
  catLevel: number       // 1/2/3 级
  parentCid: string
  children?: CategoryVO[]  // 子分类（递归嵌套）
}
```

```vue
<el-tree
  :data="treeData"
  :props="{ label: 'name', children: 'children' }"
  node-key="catId"
/>
```

| 属性 | 作用 |
|------|------|
| `:data` | 树形数据（嵌套 children） |
| `:props` | 字段映射：`label` 显示字段，`children` 子节点字段 |
| `node-key` | 节点唯一标识（类似主键） |

> **后端类比**：`node-key` 就像数据库主键，`:props` 就像 MyBatis 的字段映射。

### 6.2 自定义节点内容（#default 插槽）

通过 `#default` 插槽自定义每个节点的渲染内容：

```vue
<!-- mall-admin-frontend/src/views/product/category/index.vue -->
<el-tree :data="treeData" :props="{ label: 'name', children: 'children' }">
  <template #default="{ data }">
    <CategoryNode :data="data" @add="handleAddChild" @edit="handleEdit" @delete="handleDelete" />
  </template>
</el-tree>
```

`CategoryNode` 是自定义的节点组件，包含分类名 + 新增/编辑/删除按钮。

### 6.3 拖拽排序

```vue
<el-tree
  :draggable="dragEnabled"      <!-- 拖拽开关 -->
  :allow-drop="allowDrop"       <!-- 拖拽前校验 -->
  @node-drop="handleDrop"       <!-- 拖拽完成回调 -->
/>
```

**allow-drop（拖拽前校验）**——控制能否放到目标位置：

```ts
function allowDrop(draggingNode: Node, dropNode: Node, type: 'before' | 'after' | 'inner') {
  // type: before/after = 放到节点前/后，inner = 放到节点内部
  const draggingData = draggingNode.data as CategoryVO
  const dropData = dropNode.data as CategoryVO

  // 计算拖拽后的目标层级
  const targetLevel = type === 'inner' ? dropData.catLevel + 1 : dropData.catLevel

  // 层级不能超过 3
  if (targetLevel > 3) return false

  // 不能拖到自己子节点下（会循环引用）
  if (type === 'inner' && isDescendant(treeData.value, draggingData.catId, dropData.catId, 'catId')) {
    return false
  }
  return true
}
```

**node-drop（拖拽完成）**——收集变更并提交后端：

```ts
async function handleDrop(draggingNode: Node, dropNode: Node, dropType: 'before' | 'after' | 'inner') {
  // 计算新的 parentCid 和 catLevel
  const newParentCid = dropType === 'inner' ? dropData.catId : dropData.parentCid
  const newLevel = dropType === 'inner' ? dropData.catLevel + 1 : dropData.catLevel

  // 收集新父节点下所有子节点（按 DOM 顺序重新排序）
  const sortItems = newParentNode.childNodes.map((child, index) => ({
    catId: child.data.catId,
    parentCid: newParentCid,
    catLevel: newLevel,
    sort: index,
  }))

  await sortCategories(sortItems)  // 提交后端批量更新
}
```

### 6.4 复选框（show-checkbox）

```vue
<el-tree :data="treeData" show-checkbox @check="handleCheckChange" />
```

```ts
function handleCheckChange() {
  checkedNodes.value = treeRef.value.getCheckedNodes()  // 获取所有勾选节点
}
```

复选框用于批量删除：勾选要删除的分类，点「批量删除」按钮一次性提交。

### 6.5 展开/折叠控制

```ts
// 展开全部：递归调用 node.expand()
function expandAll() {
  const store = treeRef.value?.store
  const traverse = (node: any) => {
    node.expand(null, true)
    node.childNodes.forEach((child: any) => traverse(child))
  }
  store.root.childNodes.forEach((child: any) => traverse(child))
}
// 折叠全部同理，调用 node.collapse()
```

也可以通过 `default-expanded-keys` 指定默认展开的节点：

```ts
// 默认展开所有一级分类
defaultExpandedKeys.value = treeData.value.map((item) => item.catId)
```

## 7. 小结

| 知识点 | 核心要点 |
|------|------|
| 引入方式 | 全量引入（项目选择）vs 按需引入（resolver） |
| 布局组件 | el-container 系列，项目用 flex 自行实现等价结构 |
| 表单组件 | el-form + el-form-item + 各种输入控件，v-model 双向绑定 |
| 数据展示 | el-table + el-pagination 是列表页标配 |
| 反馈组件 | el-dialog（弹窗）/ el-message（提示）/ el-message-box（确认） |
| 主题定制 | CSS 变量覆盖，编译时生效，无 FOUC 问题 |
| 表单校验 | rules 定义规则，validate() 触发校验，类似后端 @Valid |
| el-tree | 树形数据展示 + 拖拽排序 + 复选框，分类管理核心 |

> **学习建议**：Element Plus 组件众多，不必全学。先掌握项目用到的（表单、表格、弹窗、树），其余遇到查文档即可。组件 API 的设计思想是一致的——理解一个，类推其他。
