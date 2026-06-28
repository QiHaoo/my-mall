<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTree } from 'element-plus'
import type Node from 'element-plus/es/components/tree/src/model/node'
import type { CategoryVO, CategorySortItem } from '@/api/types/product'
import { getCategoryTree, batchDeleteCategories, sortCategories } from '@/api/product/category'
import { traverseTree, isDescendant } from '@/utils/tree'
import CategoryNode from './components/CategoryNode.vue'
import CategoryForm from './components/CategoryForm.vue'

/**
 * 分类管理页面
 * 工具栏 + 树形区 + 弹窗编排
 */

const loading = ref(false)
const treeData = ref<CategoryVO[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const dragEnabled = ref(false) // 拖拽开关，默认关闭
const defaultExpandedKeys = ref<string[]>([]) // 默认展开的一级分类

// 弹窗
const formVisible = ref(false)
const formRef = ref<InstanceType<typeof CategoryForm>>()

// 选中的节点（批量删除用）
const checkedNodes = ref<CategoryVO[]>([])
const batchDeleteDisabled = computed(() => checkedNodes.value.length === 0)

// 加载分类树
async function loadTree() {
  loading.value = true
  try {
    treeData.value = await getCategoryTree()
    // 默认展开一级分类
    defaultExpandedKeys.value = treeData.value.map((item) => item.catId)
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}

// 展开全部
function expandAll() {
  const store = treeRef.value?.store
  if (!store) return
  // 使用节点 expand() 方法触发视图更新
  const traverse = (node: any) => {
    node.expand(null, true)
    if (node.childNodes) {
      node.childNodes.forEach((child: any) => traverse(child))
    }
  }
  store.root.childNodes.forEach((child: any) => traverse(child))
}

// 折叠全部
function collapseAll() {
  const store = treeRef.value?.store
  if (!store) return
  const traverse = (node: any) => {
    node.collapse()
    if (node.childNodes) {
      node.childNodes.forEach((child: any) => traverse(child))
    }
  }
  store.root.childNodes.forEach((child: any) => traverse(child))
}

// 新增一级分类
function handleAddRoot() {
  formRef.value?.openCreate()
}

// 新增子分类
function handleAddChild(data: CategoryVO) {
  formRef.value?.openCreate(data)
}

// 编辑
function handleEdit(data: CategoryVO) {
  formRef.value?.openEdit(data)
}

// 删除单个
async function handleDelete(data: CategoryVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${data.name}」及其所有子分类吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
    await batchDeleteCategories([data.catId])
    ElMessage.success('删除成功')
    loadTree()
  } catch (err) {
    // 用户取消或后端错误
  }
}

// 批量删除
async function handleBatchDelete() {
  const names = checkedNodes.value.map((n) => n.name).join('、')
  try {
    await ElMessageBox.confirm(
      `已选中 ${checkedNodes.value.length} 个分类：${names}，确定删除吗？`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
    const ids = checkedNodes.value.map((n) => n.catId)
    await batchDeleteCategories(ids)
    ElMessage.success('删除成功')
    checkedNodes.value = []
    loadTree()
  } catch (err) {
    // 用户取消或后端错误
  }
}

// 复选框变化
function handleCheckChange() {
  if (treeRef.value) {
    checkedNodes.value = treeRef.value.getCheckedNodes() as CategoryVO[]
  }
}

// 弹窗操作成功后刷新
function handleFormSuccess() {
  loadTree()
}

// ===== 拖拽排序 =====

// 拖拽前校验（allow-drop）
function allowDrop(draggingNode: Node, dropNode: Node, type: 'before' | 'after' | 'inner') {
  if (!draggingNode.data || !dropNode.data) return false
  const draggingData = draggingNode.data as CategoryVO
  const dropData = dropNode.data as CategoryVO

  // 计算拖拽后的目标层级
  let targetLevel: number
  if (type === 'inner') {
    targetLevel = dropData.catLevel + 1
  } else {
    targetLevel = dropData.catLevel
  }

  // 层级不能超过 3
  if (targetLevel > 3) return false

  // 拖拽节点的子节点最大深度 + 目标层级不能超过 3
  let maxChildLevel = 0
  if (draggingData.children) {
    traverseTree(draggingData.children, (node) => {
      const relativeLevel = node.catLevel - draggingData.catLevel
      if (relativeLevel > maxChildLevel) maxChildLevel = relativeLevel
    })
  }
  if (targetLevel + maxChildLevel > 3) return false

  // 不能拖到自己子节点下（循环引用）
  if (type === 'inner') {
    if (isDescendant(treeData.value, draggingData.catId, dropData.catId, 'catId')) {
      return false
    }
  }

  return true
}

// 拖拽完成
async function handleDrop(
  draggingNode: Node,
  dropNode: Node,
  dropType: 'before' | 'after' | 'inner'
) {
  if (!draggingNode.data || !dropNode.data) return
  const draggingData = draggingNode.data as CategoryVO
  const dropData = dropNode.data as CategoryVO

  // 计算变更
  let newParentCid: string
  let newLevel: number

  if (dropType === 'inner') {
    newParentCid = dropData.catId
    newLevel = dropData.catLevel + 1
  } else {
    newParentCid = dropData.parentCid
    newLevel = dropData.catLevel
  }

  // 构造变更预览信息
  const fromParentName = draggingData.parentCid === '0'
    ? '顶级'
    : (treeRef.value?.store?.getNode(draggingData.parentCid)?.data?.name || '未知')
  const toParentName = newParentCid === '0'
    ? '顶级'
    : (treeRef.value?.store?.getNode(newParentCid)?.data?.name || dropData.name)

  // 收集受影响节点（新父节点下的所有同层级节点，按当前 DOM 顺序重新排序）
  const store = treeRef.value?.store
  if (!store) return

  const newParentNode = newParentCid === 0 ? store.root : store.getNode(newParentCid)
  if (!newParentNode) return

  const sortItems: CategorySortItem[] = newParentNode.childNodes.map(
    (child: any, index: number) => ({
      catId: child.data.catId,
      parentCid: newParentCid,
      catLevel: newLevel,
      sort: index
    })
  )

  // 如果有子节点层级也需要更新（跨层级拖拽时子节点 catLevel 变化）
  if (draggingData.catLevel !== newLevel) {
    const levelDiff = newLevel - draggingData.catLevel
    // 递归收集拖拽节点的所有子孙节点
    const collectDescendants = (node: CategoryVO, currentLevel: number) => {
      sortItems.push({
        catId: node.catId,
        parentCid: node.parentCid,
        catLevel: currentLevel,
        sort: node.sort
      })
      if (node.children) {
        node.children.forEach((child) => {
          collectDescendants(child, currentLevel + (child.catLevel - node.catLevel) + levelDiff)
        })
      }
    }
    // 注：这里子孙节点的 parentCid 不变，只 catLevel 变
    // 简化处理：拖拽节点自身的 sort 已在上面包含，子孙节点只需更新 catLevel
    if (draggingData.children) {
      const updateChildrenLevel = (nodes: CategoryVO[], oldParentLevel: number) => {
        nodes.forEach((child) => {
          const newChildLevel = oldParentLevel + 1
          sortItems.push({
            catId: child.catId,
            parentCid: child.parentCid,
            catLevel: newChildLevel,
            sort: child.sort
          })
          if (child.children) {
            updateChildrenLevel(child.children, newChildLevel)
          }
        })
      }
      updateChildrenLevel(draggingData.children, newLevel)
    }
  }

  // 弹确认框
  try {
    await ElMessageBox.confirm(
      `将「${draggingData.name}」从「${fromParentName}」移动到「${toParentName}」下，确认操作？`,
      '拖拽确认',
      { type: 'info', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
    await sortCategories(sortItems)
    ElMessage.success('排序成功')
    loadTree()
  } catch (err) {
    // 用户取消或后端错误，重新加载树回滚
    loadTree()
  }
}

onMounted(() => {
  loadTree()
})
</script>

<template>
  <div class="category-page app-container">
    <!-- 工具栏 -->
    <div class="toolbar card-box">
      <div class="toolbar-left">
        <span class="drag-switch-label">开启拖拽</span>
        <el-switch v-model="dragEnabled" />
        <el-button @click="expandAll">展开全部</el-button>
        <el-button @click="collapseAll">折叠全部</el-button>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="handleAddRoot">
          <el-icon><Plus /></el-icon> 新增一级分类
        </el-button>
        <el-button
          type="danger"
          :disabled="batchDeleteDisabled"
          @click="handleBatchDelete"
        >
          <el-icon><Delete /></el-icon> 批量删除
        </el-button>
      </div>
    </div>

    <!-- 树形区 -->
    <div class="tree-container card-box" v-loading="loading">
      <el-tree
        ref="treeRef"
        :data="treeData"
        :props="{ label: 'name', children: 'children' }"
        node-key="catId"
        :default-expanded-keys="defaultExpandedKeys"
        show-checkbox
        :draggable="dragEnabled"
        :allow-drop="allowDrop"
        @node-drop="handleDrop"
        @check="handleCheckChange"
      >
        <template #default="{ data }">
          <CategoryNode
            :data="data"
            @add="handleAddChild"
            @edit="handleEdit"
            @delete="handleDelete"
          />
        </template>
      </el-tree>

      <el-empty v-if="!loading && treeData.length === 0" description="暂无分类数据" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <CategoryForm
      ref="formRef"
      v-model="formVisible"
      @success="handleFormSuccess"
    />
  </div>
</template>

<style scoped lang="scss">
.category-page {
  .toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 20px;
    margin-bottom: 16px;

    .toolbar-left {
      display: flex;
      align-items: center;
      gap: 12px;

      .drag-switch-label {
        font-size: $font-size-base;
        color: $text-regular;
      }
    }

    .toolbar-right {
      display: flex;
      gap: 8px;
    }
  }

  .tree-container {
    padding: 20px;
    min-height: 400px;

    :deep(.el-tree-node__content) {
      height: 36px;
    }
  }
}
</style>
