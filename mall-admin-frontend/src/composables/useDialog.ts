import { ref } from 'vue'

/**
 * 弹窗状态管理 Composable
 * 封装弹窗可见性、模式（新增/编辑）、表单数据
 *
 * @param defaultData 表单默认数据（新增时使用）
 */
export function useDialog<T extends Record<string, any>>(defaultData: T) {
  const visible = ref(false)
  const mode = ref<'create' | 'update'>('create')
  const formData = ref<T>({ ...defaultData }) as ReturnType<typeof ref<T>>

  /** 打开弹窗，传入 data 为编辑模式，不传为新增模式 */
  function open(data?: Partial<T>) {
    if (data) {
      mode.value = 'update'
      formData.value = { ...defaultData, ...data }
    } else {
      mode.value = 'create'
      formData.value = { ...defaultData }
    }
    visible.value = true
  }

  /** 关闭弹窗 */
  function close() {
    visible.value = false
  }

  return {
    visible,
    mode,
    formData,
    open,
    close
  }
}
