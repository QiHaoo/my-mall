import { ref, type Ref } from 'vue'
import type { PageVO } from '@/api/types/common'

/**
 * 分页表格状态管理 Composable
 * 封装分页查询、搜索、重置、翻页等通用逻辑
 *
 * @param fetchFn 分页查询函数，接收 { pageNum, pageSize, ...search } 参数，返回 PageVO<T>
 */
export function useTable<T>(fetchFn: (params: Record<string, any>) => Promise<PageVO<T>>) {
  const data: Ref<T[]> = ref([])
  const loading = ref(false)
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const searchParams = ref<Record<string, any>>({})

  async function loadData() {
    loading.value = true
    try {
      const res = await fetchFn({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...searchParams.value
      })
      data.value = res.records
      total.value = Number(res.total)
    } finally {
      loading.value = false
    }
  }

  /** 搜索（重置到第一页） */
  function handleSearch() {
    pageNum.value = 1
    loadData()
  }

  /** 重置搜索条件 */
  function handleReset() {
    searchParams.value = {}
    pageNum.value = 1
    loadData()
  }

  /** 页码变化 */
  function handlePageChange(page: number) {
    pageNum.value = page
    loadData()
  }

  /** 每页条数变化 */
  function handleSizeChange(size: number) {
    pageSize.value = size
    pageNum.value = 1
    loadData()
  }

  /** 刷新当前页 */
  function refresh() {
    loadData()
  }

  return {
    data,
    loading,
    total,
    pageNum,
    pageSize,
    searchParams,
    loadData,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
    refresh
  }
}
