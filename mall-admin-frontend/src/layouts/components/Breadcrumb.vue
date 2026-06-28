<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

// 从路由 matched 生成面包屑
const breadcrumbs = computed(() => {
  return route.matched.filter((item) => item.meta && item.meta.title)
})
</script>

<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item
      v-for="item in breadcrumbs"
      :key="item.path"
      :to="item.path"
    >
      {{ item.meta.title }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>
