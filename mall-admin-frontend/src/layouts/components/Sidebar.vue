<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import { useRouter, useRoute } from 'vue-router'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import type { RouteRecordRaw } from 'vue-router'

const appStore = useAppStore()
const router = useRouter()
const route = useRoute()

// 从路由配置中提取 AdminLayout 的 children 作为菜单项
const menuItems = computed(() => {
  const rootRoute = router.options.routes.find(
    (r) => r.path === '/'
  ) as RouteRecordRaw | undefined
  return rootRoute?.children || []
})

// 当前激活的菜单
const activeMenu = computed(() => route.path)

// 图标组件映射
const iconMap = ElementPlusIconsVue

function handleSelect(index: string) {
  router.push(index)
}
</script>

<template>
  <div class="sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <div class="logo">
      <span v-if="!appStore.sidebarCollapsed" class="logo-text">商城管理</span>
      <span v-else class="logo-icon">M</span>
    </div>
    <el-menu
      :default-active="activeMenu"
      :collapse="appStore.sidebarCollapsed"
      :collapse-transition="false"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409eff"
      @select="handleSelect"
    >
      <el-menu-item
        v-for="item in menuItems"
        :key="item.path"
        :index="'/' + item.path"
      >
        <el-icon v-if="item.meta?.icon">
          <component :is="iconMap[item.meta.icon as keyof typeof iconMap]" />
        </el-icon>
        <template #title>{{ item.meta?.title }}</template>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<style scoped lang="scss">
.sidebar {
  width: $sidebar-width;
  height: 100%;
  background-color: $sidebar-bg;
  transition: width 0.3s;
  overflow: hidden;
  flex-shrink: 0;

  &.collapsed {
    width: $sidebar-collapsed-width;
  }
}

.logo {
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: $font-size-lg;
  font-weight: 600;
  border-bottom: 1px solid #1f2d3d;

  .logo-text {
    white-space: nowrap;
  }

  .logo-icon {
    font-size: $font-size-xl;
  }
}

:deep(.el-menu) {
  border-right: none;
}
</style>
