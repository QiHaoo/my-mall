<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { BrandVO, BrandSaveDTO } from '@/api/types/product'
import { createBrand, updateBrand, getBrandDetail } from '@/api/product/brand'

/**
 * 品牌新增/编辑表单弹窗
 * - 新增：空表单
 * - 编辑：先拉取详情（含 version），回填表单
 * - Logo 字段本期使用 URL 输入 + 预览，后续接入 OSS 直传
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
const mode = ref<'create' | 'update'>('create')

const formData = ref<BrandSaveDTO>({
  name: '',
  logo: '',
  descript: '',
  showStatus: 1,
  firstLetter: '',
  sort: 0
})

// 校验规则
const rules: FormRules = {
  name: [
    { required: true, message: '品牌名称不能为空', trigger: 'blur' },
    { max: 64, message: '品牌名称最长 64 字符', trigger: 'blur' }
  ],
  logo: [
    { required: true, message: '品牌 Logo 不能为空', trigger: 'blur' },
    { max: 1024, message: 'Logo 地址最长 1024 字符', trigger: 'blur' }
  ],
  firstLetter: [
    {
      pattern: /^[A-Za-z]$/,
      message: '首字母必须为单个字母',
      trigger: 'blur'
    }
  ],
  descript: [{ max: 500, message: '品牌介绍最长 500 字符', trigger: 'blur' }]
}

// 弹窗标题
const dialogTitle = computed(() => (mode.value === 'create' ? '新增品牌' : '编辑品牌'))

/**
 * 打开新增弹窗
 */
function openCreate() {
  mode.value = 'create'
  formData.value = {
    name: '',
    logo: '',
    descript: '',
    showStatus: 1,
    firstLetter: '',
    sort: 0
  }
  visible.value = true
}

/**
 * 打开编辑弹窗（先拉取详情获取 version）
 */
async function openEdit(row: BrandVO) {
  mode.value = 'update'
  visible.value = true
  try {
    const detail = await getBrandDetail(row.id)
    formData.value = {
      id: detail.id,
      name: detail.name,
      logo: detail.logo,
      descript: detail.descript || '',
      showStatus: detail.showStatus,
      firstLetter: detail.firstLetter || '',
      sort: detail.sort,
      version: detail.version
    }
  } catch {
    // 错误已由拦截器处理
    visible.value = false
  }
}

// 提交
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true
    if (mode.value === 'create') {
      await createBrand(formData.value)
      ElMessage.success('新增成功')
    } else {
      await updateBrand(formData.value) // 携带 version 触发乐观锁
      ElMessage.success('修改成功')
    }
    emit('success')
    visible.value = false
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

// 弹窗关闭时重置校验
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
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="品牌名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入品牌名称" maxlength="64" />
      </el-form-item>

      <el-form-item label="品牌 Logo" prop="logo">
        <div class="logo-uploader">
          <div v-if="formData.logo" class="logo-preview">
            <el-image :src="formData.logo" fit="contain" class="logo-img" />
            <el-button link type="danger" @click="formData.logo = ''">移除</el-button>
          </div>
          <el-input
            v-model="formData.logo"
            placeholder="请输入 Logo 图片 URL"
            maxlength="1024"
          >
            <template #prepend>URL</template>
          </el-input>
          <div class="logo-tip">本期通过 URL 输入 Logo 地址，后续支持 OSS 直传</div>
        </div>
      </el-form-item>

      <el-form-item label="品牌介绍" prop="descript">
        <el-input
          v-model="formData.descript"
          type="textarea"
          :rows="3"
          placeholder="请输入品牌介绍"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="首字母" prop="firstLetter">
        <el-input
          v-model="formData.firstLetter"
          placeholder="如：X"
          maxlength="1"
          style="width: 120px"
        />
      </el-form-item>

      <el-form-item label="排序值" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" controls-position="right" />
      </el-form-item>

      <el-form-item label="显示状态" prop="showStatus">
        <el-switch
          v-model="formData.showStatus"
          :active-value="1"
          :inactive-value="0"
          active-text="显示"
          inactive-text="隐藏"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.logo-uploader {
  width: 100%;

  .logo-preview {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 8px;

    .logo-img {
      width: 120px;
      height: 80px;
      border: 1px solid $border-lighter;
      border-radius: $radius-base;
      background-color: $bg-page;
    }
  }

  .logo-tip {
    font-size: $font-size-xs;
    color: $text-secondary;
    margin-top: 4px;
  }
}
</style>
