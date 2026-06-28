<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { BrandVO } from '@/api/types/product'
import {
  getBrandPage,
  deleteBrand,
  batchDeleteBrands,
  updateBrandShowStatus
} from '@/api/product/brand'
import PageTable from '@/components/PageTable/index.vue'
import BrandForm from './components/BrandForm.vue'
import BrandRelationDialog from './components/BrandRelationDialog.vue'

/**
 * 品牌管理页面
 * 筛选 + 表格 + 分页 + 批量操作 + 显示状态切换 + 关联分类
 */

// 搜索字段配置
const letterOptions = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').map((l) => ({
  label: l,
  value: l
}))
const statusOptions = [
  { label: '显示', value: 1 },
  { label: '隐藏', value: 0 }
]
const searchFields = [
  { prop: 'name', label: '品牌名', component: 'input' as const, placeholder: '请输入品牌名', width: '200px' },
  { prop: 'firstLetter', label: '首字母', component: 'select' as const, placeholder: '请选择', options: letterOptions, width: '120px' },
  { prop: 'showStatus', label: '显示状态', component: 'select' as const, placeholder: '请选择', options: statusOptions, width: '120px' }
]

// 表格列配置
const columns = [
  { prop: 'logo', label: 'Logo', width: 120, slot: true },
  { prop: 'name', label: '品牌名称', width: 180 },
  { prop: 'descript', label: '品牌介绍', width: 380, slot: true },
  { prop: 'firstLetter', label: '首字母', width: 100, align: 'center' as const },
  { prop: 'sort', label: '排序', width: 100, align: 'center' as const },
  { prop: 'showStatus', label: '显示状态', width: 120, slot: true },
  { prop: 'actions', label: '操作', width: 240, fixed: 'right' as const, slot: true }
]

// 表格 ref（PageTable 内部维护分页状态，通过 ref 暴露 data / pageNum 等）
const tableRef = ref<InstanceType<typeof PageTable>>()

// 批量选择
const selectedRows = ref<BrandVO[]>([])
const selectedIds = computed(() => selectedRows.value.map((r) => r.id))

function handleSelectionChange(rows: BrandVO[]) {
  selectedRows.value = rows
}

// 品牌表单弹窗
const formVisible = ref(false)
const formRef = ref<InstanceType<typeof BrandForm>>()

function handleAdd() {
  formRef.value?.openCreate()
}

function handleEdit(row: BrandVO) {
  formRef.value?.openEdit(row)
}

function handleFormSuccess() {
  tableRef.value?.refresh()
}

// 显示状态切换（失败回滚）
async function handleStatusChange(row: BrandVO, val: boolean) {
  const newStatus = val ? 1 : 0
  try {
    await updateBrandShowStatus(row.id, newStatus)
    row.showStatus = newStatus
    ElMessage.success('状态更新成功')
  } catch {
    // 失败不修改 row.showStatus，el-switch 自动回滚
  }
}

// 单个删除
async function handleDelete(row: BrandVO) {
  try {
    await ElMessageBox.confirm(`确定删除品牌「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
    await deleteBrand(row.id)
    ElMessage.success('删除成功')
    // 若当前页只剩一条且不是第一页，回退到上一页
    const table = tableRef.value
    if (table && table.tableData.value.length === 1 && table.pageNum.value > 1) {
      table.pageNum.value--
    }
    tableRef.value?.refresh()
  } catch (err) {
    // 用户取消或后端错误
  }
}

// 批量删除
async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(
      `已选中 ${selectedIds.value.length} 个品牌，确定批量删除吗？`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
    await batchDeleteBrands(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedRows.value = []
    // 若当前页数据全部删除完且不是第一页，回退
    const table = tableRef.value
    if (table && table.tableData.value.length === selectedIds.value.length && table.pageNum.value > 1) {
      table.pageNum.value--
    }
    tableRef.value?.refresh()
  } catch (err) {
    // 用户取消或后端错误
  }
}

// 关联分类弹窗
const relationVisible = ref(false)
const relationBrand = ref<{ id: string; name: string }>({ id: '', name: '' })

function handleRelation(row: BrandVO) {
  relationBrand.value = { id: row.id, name: row.name }
  relationVisible.value = true
}
</script>

<template>
  <div class="brand-page app-container">
    <PageTable
      ref="tableRef"
      :columns="columns"
      :fetch="getBrandPage"
      :search-fields="searchFields"
      selectable
      @selection-change="handleSelectionChange"
    >
      <template #toolbar>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增品牌
        </el-button>
        <el-button
          type="danger"
          :disabled="selectedIds.length === 0"
          @click="handleBatchDelete"
        >
          <el-icon><Delete /></el-icon> 批量删除
        </el-button>
      </template>

      <!-- Logo 列 -->
      <template #col-logo="{ row }">
        <el-image
          v-if="row.logo"
          :src="row.logo"
          fit="contain"
          style="width: 60px; height: 40px"
          class="brand-logo"
        />
        <span v-else class="text-secondary">暂无</span>
      </template>

      <!-- 品牌介绍列（截断） -->
      <template #col-descript="{ row }">
        <span class="text-ellipsis" :title="row.descript">{{ row.descript || '-' }}</span>
      </template>

      <!-- 显示状态列 -->
      <template #col-showStatus="{ row }">
        <el-switch
          :model-value="row.showStatus === 1"
          @change="(val: boolean) => handleStatusChange(row, val)"
        />
      </template>

      <!-- 操作列 -->
      <template #col-actions="{ row }">
        <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="primary" @click="handleRelation(row)">关联分类</el-button>
        <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
      </template>
    </PageTable>

    <!-- 品牌表单弹窗 -->
    <BrandForm
      ref="formRef"
      v-model="formVisible"
      @success="handleFormSuccess"
    />

    <!-- 关联分类弹窗 -->
    <BrandRelationDialog
      v-model="relationVisible"
      :brand-id="relationBrand.id"
      :brand-name="relationBrand.name"
    />
  </div>
</template>

<style scoped lang="scss">
.brand-page {
  .brand-logo {
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    background-color: $bg-page;
  }

  .text-secondary {
    color: $text-secondary;
    font-size: $font-size-sm;
  }
}
</style>
