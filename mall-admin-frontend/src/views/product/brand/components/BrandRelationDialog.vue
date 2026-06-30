<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { BrandRelationVO, CategoryVO } from '@/api/types/product'
import {
  getBrandRelations,
  createBrandRelation,
  deleteBrandRelation
} from '@/api/product/brand'
import { getCategoryTree } from '@/api/product/category'

/**
 * 品牌-分类关联管理弹窗
 * - 查询已关联分类列表
 * - 新增关联（选择三级分类）
 * - 移除关联
 */

const props = defineProps<{
  modelValue: boolean
  brandId: string
  brandName: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const relations = ref<BrandRelationVO[]>([])

// 新增关联相关
const showAddForm = ref(false)
const categoryTree = ref<CategoryVO[]>([])
const selectedCategoryId = ref<string | undefined>(undefined)

// 加载关联列表
async function loadRelations() {
  loading.value = true
  try {
    relations.value = await getBrandRelations(props.brandId)
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}

// 加载分类树（仅三级可选）
async function loadCategoryTree() {
  try {
    categoryTree.value = await getCategoryTree()
  } catch {
    // 错误已由拦截器处理
  }
}

// 打开新增关联表单
async function handleShowAdd() {
  showAddForm.value = true
  selectedCategoryId.value = undefined
  if (categoryTree.value.length === 0) {
    await loadCategoryTree()
  }
}

// 确认新增关联
async function handleAddRelation() {
  if (!selectedCategoryId.value) {
    ElMessage.warning('请选择三级分类')
    return
  }
  try {
    await createBrandRelation(props.brandId, selectedCategoryId.value)
    ElMessage.success('关联成功')
    showAddForm.value = false
    selectedCategoryId.value = undefined
    loadRelations()
  } catch {
    // 错误已由拦截器处理
  }
}

// 移除关联
async function handleRemoveRelation(row: BrandRelationVO) {
  try {
    await ElMessageBox.confirm(
      `确定移除品牌「${row.brandName}」与分类「${row.categoryName}」的关联吗？`,
      '移除确认',
      { type: 'warning', confirmButtonText: '确定移除', cancelButtonText: '取消' }
    )
    await deleteBrandRelation(props.brandId, row.categoryId)
    ElMessage.success('移除成功')
    loadRelations()
  } catch (err) {
    // 用户取消或后端错误
  }
}

// 弹窗打开时加载关联列表
watch(visible, (val) => {
  if (val && props.brandId) {
    loadRelations()
  } else {
    showAddForm.value = false
  }
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="关联分类"
    width="520px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="relation-header">
      <span class="brand-label">品牌：{{ brandName }}</span>
      <el-button v-if="!showAddForm" type="primary" size="small" @click="handleShowAdd">
        <el-icon><Plus /></el-icon> 新增关联
      </el-button>
    </div>

    <!-- 新增关联表单 -->
    <div v-if="showAddForm" class="add-form">
      <el-tree-select
        v-model="selectedCategoryId"
        :data="categoryTree"
        :props="{ label: 'name', value: 'id', children: 'children', disabled: (data: CategoryVO) => data.level !== 3 }"
        check-strictly
        node-key="id"
        placeholder="请选择三级分类"
        style="width: 100%"
      />
      <div class="add-form-actions">
        <el-button size="small" @click="showAddForm = false">取消</el-button>
        <el-button type="primary" size="small" @click="handleAddRelation">确认关联</el-button>
      </div>
    </div>

    <!-- 关联列表 -->
    <el-table v-loading="loading" :data="relations" border stripe style="width: 100%">
      <el-table-column type="index" label="#" width="50" align="center" />
      <el-table-column prop="brandName" label="品牌名" width="120" />
      <el-table-column prop="categoryName" label="分类名" />
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="handleRemoveRelation(row)">
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!loading && relations.length === 0"
      description="暂无关联分类"
      :image-size="80"
    />

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.relation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  .brand-label {
    font-size: $font-size-base;
    color: $text-primary;
    font-weight: 500;
  }
}

.add-form {
  margin-bottom: 16px;
  padding: 16px;
  background-color: $bg-page;
  border-radius: $radius-base;

  .add-form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 12px;
  }
}
</style>
