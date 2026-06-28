<script setup lang="ts">
import type { CategoryVO } from '@/api/types/product'

/**
 * 分类树节点行渲染组件
 * 展示分类名称 + 层级标签 + 行内操作按钮（默认显示）
 */

defineProps<{
  data: CategoryVO
}>()

const emit = defineEmits<{
  (e: 'add', data: CategoryVO): void
  (e: 'edit', data: CategoryVO): void
  (e: 'delete', data: CategoryVO): void
}>()

// 层级标签类型映射
const levelTagType = computed(() => {
  return (level: number) => {
    if (level === 1) return undefined // 蓝色（默认）
    if (level === 2) return 'success' // 绿色
    return 'info' // 灰色
  }
})

// 层级标签文字
const levelLabel = computed(() => {
  return (level: number) => {
    if (level === 1) return '一级'
    if (level === 2) return '二级'
    return '三级'
  }
})
</script>

<template>
  <div class="category-node">
    <span class="node-name">{{ data.name }}</span>
    <el-tag :type="levelTagType(data.catLevel)" size="small" class="node-level">
      {{ levelLabel(data.catLevel) }}
    </el-tag>
    <span v-if="data.productCount" class="node-count">商品: {{ data.productCount }}</span>
    <span class="node-actions">
      <el-button link type="primary" size="small" @click.stop="emit('add', data)">
        <el-icon><Plus /></el-icon> 新增
      </el-button>
      <el-button link type="primary" size="small" @click.stop="emit('edit', data)">
        <el-icon><Edit /></el-icon> 编辑
      </el-button>
      <el-button
        v-if="data.catLevel > 1"
        link
        type="danger"
        size="small"
        @click.stop="emit('delete', data)"
      >
        <el-icon><Delete /></el-icon> 删除
      </el-button>
    </span>
  </div>
</template>

<style scoped lang="scss">
.category-node {
  display: flex;
  align-items: center;
  flex: 1;
  padding-right: 8px;

  .node-name {
    font-size: $font-size-base;
    color: $text-primary;
    margin-right: 8px;
  }

  .node-level {
    margin-right: 8px;
  }

  .node-count {
    font-size: $font-size-xs;
    color: $text-secondary;
  }

  .node-actions {
    margin-left: auto;
    white-space: nowrap;
  }
}
</style>
