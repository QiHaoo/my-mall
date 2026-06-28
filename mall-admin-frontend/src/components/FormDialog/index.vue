<script setup lang="ts" generic="T extends Record<string, any>">
import { ref, watch, nextTick } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

/**
 * 通用表单弹窗组件
 * 封装 el-dialog + el-form + 校验 + 提交 loading
 */

const props = withDefaults(
  defineProps<{
    /** 弹窗可见性（v-model） */
    modelValue: boolean
    /** 弹窗标题 */
    title: string
    /** 弹窗宽度 */
    width?: string
    /** 表单初始数据（编辑时回填） */
    initialData: T
    /** 提交函数，返回 Promise，自动管理 loading */
    submit: (data: T) => Promise<void>
    /** 表单校验规则 */
    rules?: FormRules
    /** 表单 label 宽度 */
    labelWidth?: string
  }>(),
  {
    width: '520px',
    labelWidth: '100px'
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
// 表单数据副本（避免直接修改父组件传入的数据）
const formData = ref<T>({ ...props.initialData }) as ReturnType<typeof ref<T>>

// 弹窗打开时重置表单数据
watch(
  () => props.modelValue,
  async (val) => {
    if (val) {
      formData.value = { ...props.initialData }
      await nextTick()
      formRef.value?.clearValidate()
    }
  }
)

// 同步 visible
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 提交
async function handleSubmit() {
  if (!formRef.value) return
  try {
    // 有规则时才校验
    if (props.rules) {
      await formRef.value.validate()
    }
    submitting.value = true
    await props.submit(formData.value)
    emit('success')
    dialogVisible.value = false
  } catch (err) {
    // 校验失败或提交失败，不关闭弹窗
    if (err !== false) {
      // 提交失败的错误已由拦截器处理，这里只阻止关闭
    }
  } finally {
    submitting.value = false
  }
}

// 取消
function handleCancel() {
  dialogVisible.value = false
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-width="labelWidth"
    >
      <!-- 通过默认插槽渲染表单字段，提供 formData 给父组件 -->
      <slot :form-model="formData" :form-ref="formRef" />
    </el-form>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>
