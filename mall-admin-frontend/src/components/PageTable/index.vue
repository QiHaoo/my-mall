<script setup lang="ts">
import type { PageVO } from '@/api/types/common'

/**
 * 通用分页表格组件
 * 封装「搜索栏 + 工具栏插槽 + el-table + el-pagination」标准 CRUD 列表模式
 */

/** 列配置 */
interface Column {
  prop: string
  label: string
  width?: number | string
  align?: 'left' | 'center' | 'right'
  fixed?: boolean | 'left' | 'right'
  /** 是否使用插槽自定义列内容，插槽名为 col-{prop} */
  slot?: boolean
}

/** 搜索字段配置 */
interface SearchField {
  prop: string
  label: string
  component: 'input' | 'select'
  placeholder?: string
  options?: { label: string; value: string | number }[]
  width?: string
}

const props = withDefaults(
  defineProps<{
    /** 列配置 */
    columns: Column[]
    /** 分页查询函数 */
    fetch: (params: Record<string, any>) => Promise<PageVO<any>>
    /** 搜索栏字段配置 */
    searchFields?: SearchField[]
    /** 行数据主键 */
    rowKey?: string
    /** 是否显示复选框列 */
    selectable?: boolean
    /** 默认每页条数 */
    defaultPageSize?: number
  }>(),
  {
    rowKey: 'id',
    selectable: false,
    defaultPageSize: 10,
    searchFields: () => []
  }
)

const emit = defineEmits<{
  /** 复选框选择变化 */
  (e: 'selection-change', rows: any[]): void
}>()

const tableData = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(props.defaultPageSize)
const searchParams = ref<Record<string, any>>({})
const tableRef = ref()

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const res = await props.fetch({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...searchParams.value
    })
    tableData.value = res.records
    total.value = Number(res.total)
  } finally {
    loading.value = false
  }
}

// 搜索
function handleSearch() {
  pageNum.value = 1
  loadData()
}

// 重置
function handleReset() {
  searchParams.value = {}
  pageNum.value = 1
  loadData()
}

// 翻页
function handlePageChange(page: number) {
  pageNum.value = page
  loadData()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  pageNum.value = 1
  loadData()
}

// 复选框
function handleSelectionChange(rows: any[]) {
  emit('selection-change', rows)
}

/** 刷新表格（供父组件通过 ref 调用） */
function refresh() {
  loadData()
}

onMounted(() => {
  loadData()
})

defineExpose({ refresh, loadData, tableData, pageNum, pageSize, total })
</script>

<template>
  <div class="page-table">
    <!-- 搜索栏 -->
    <div v-if="searchFields.length" class="search-bar card-box">
      <el-form :inline="true" :model="searchParams" class="search-form">
        <el-form-item
          v-for="field in searchFields"
          :key="field.prop"
          :label="field.label"
        >
          <el-input
            v-if="field.component === 'input'"
            v-model="searchParams[field.prop]"
            :placeholder="field.placeholder || '请输入'"
            clearable
            :style="field.width ? { width: field.width } : {}"
            @keyup.enter="handleSearch"
          />
          <el-select
            v-else-if="field.component === 'select'"
            v-model="searchParams[field.prop]"
            :placeholder="field.placeholder || '请选择'"
            clearable
            :style="field.width ? { width: field.width } : {}"
          >
            <el-option
              v-for="opt in field.options"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-container card-box">
      <!-- 工具栏插槽 -->
      <div v-if="$slots.toolbar" class="toolbar">
        <slot name="toolbar" />
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="tableData"
        :row-key="rowKey"
        stripe
        border
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="selectable" type="selection" width="55" />
        <el-table-column
          v-for="col in columns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :align="col.align || 'left'"
          :fixed="col.fixed"
          show-overflow-tooltip
        >
          <template v-if="col.slot" #default="scope">
            <slot :name="`col-${col.prop}`" :row="scope.row" :index="scope.$index" />
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.page-table {
  .search-bar {
    padding: 18px 20px 0;
    margin-bottom: 16px;

    .search-form {
      display: flex;
      flex-wrap: wrap;
    }
  }

  .table-container {
    padding: 20px;
  }

  .toolbar {
    margin-bottom: 16px;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>
