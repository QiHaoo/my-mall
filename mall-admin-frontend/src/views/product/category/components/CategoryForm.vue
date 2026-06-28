<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { CategoryVO, CategorySaveDTO, CategoryUpdateDTO } from '@/api/types/product'
import { createCategory, updateCategory, getCategoryTree } from '@/api/product/category'

/**
 * 分类新增/编辑表单弹窗
 * - 新增模式：填写名称、父级、排序、图标、计量单位
 * - 编辑模式：回填数据，上级分类只读（层级调整走拖拽）
 */

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'success'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const formRef = ref<FormInstance>()
const submitting = ref(false)
const treeData = ref<CategoryVO[]>([])

// 弹窗模式与表单数据
const mode = ref<'create' | 'update'>('create')
const lockedParent = ref(false) // 父级分类是否锁定（新增子分类时）
const formData = ref<CategorySaveDTO & Partial<CategoryUpdateDTO>>({
  name: '',
  parentCid: '0',
  sort: 0,
  icon: '',
  productUnit: ''
})

// 校验规则
const rules: FormRules = {
  name: [
    { required: true, message: '分类名称不能为空', trigger: 'blur' },
    { max: 50, message: '分类名称最长 50 字符', trigger: 'blur' }
  ],
  parentCid: [
    { required: true, message: '上级分类不能为空', trigger: 'change' }
  ]
}

// 弹窗标题
const dialogTitle = computed(() => mode.value === 'create' ? '新增分类' : '编辑分类')

/**
 * 打开新增弹窗
 * @param parentNode 父节点，传入则锁定为该节点的子分类；不传则为一级分类
 */
async function openCreate(parentNode?: CategoryVO) {
  mode.value = 'create'
  lockedParent.value = !!parentNode
  formData.value = {
    name: '',
    parentCid: parentNode ? parentNode.catId : '0',
    sort: 0,
    icon: '',
    productUnit: ''
  }
  // 加载分类树供父级选择
  await loadTreeData()
  visible.value = true
}

/**
 * 打开编辑弹窗
 */
async function openEdit(node: CategoryVO) {
  mode.value = 'update'
  lockedParent.value = true // 编辑时父级只读
  formData.value = {
    catId: node.catId,
    name: node.name,
    parentCid: node.parentCid,
    sort: node.sort,
    icon: node.icon || '',
    productUnit: node.productUnit || ''
  }
  await loadTreeData()
  visible.value = true
}

// 加载分类树（用于父级选择器）
async function loadTreeData() {
  try {
    treeData.value = await getCategoryTree()
  } catch {
    // 错误已由拦截器处理
  }
}

// 提交
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true
    if (mode.value === 'create') {
      const { name, parentCid, sort, icon, productUnit } = formData.value
      await createCategory({ name, parentCid, sort, icon, productUnit })
      ElMessage.success('新增成功')
    } else {
      const { catId, name, sort, icon, productUnit } = formData.value
      await updateCategory({ catId: catId!, name, sort, icon, productUnit })
      ElMessage.success('修改成功')
    }
    visible.value = false
    emit('success')
  } catch (err) {
    // 校验失败或提交失败
  } finally {
    submitting.value = false
  }
}

// 取消
function handleCancel() {
  visible.value = false
}

// 清空表单校验
watch(visible, (val) => {
  if (!val) {
    formRef.value?.resetFields()
  }
})

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="520px"
    :close-on-click-modal="false"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="分类名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入分类名称" maxlength="50" show-word-limit />
      </el-form-item>

      <el-form-item label="上级分类" prop="parentCid">
        <el-tree-select
          v-model="formData.parentCid"
          :data="treeData"
          :props="{ label: 'name', value: 'catId', children: 'children' }"
          :disabled="lockedParent || mode === 'update'"
          check-strictly
          node-key="catId"
          placeholder="请选择上级分类"
          style="width: 100%"
        >
          <template #default="{ data: node }">
            <span>{{ node.name }}</span>
            <span v-if="node.catLevel" style="color: #909399; font-size: 12px; margin-left: 4px;">
              ({{ node.catLevel }}级)
            </span>
          </template>
        </el-tree-select>
      </el-form-item>

      <el-form-item label="排序值" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" controls-position="right" />
      </el-form-item>

      <el-form-item label="图标地址" prop="icon">
        <el-input v-model="formData.icon" placeholder="图标 URL（非必填）" maxlength="255" />
      </el-form-item>

      <el-form-item label="计量单位" prop="productUnit">
        <el-input v-model="formData.productUnit" placeholder="如：件、台、个" maxlength="50" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
