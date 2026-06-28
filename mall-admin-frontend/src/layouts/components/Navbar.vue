<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import { Fold, Expand } from '@element-plus/icons-vue'
import Breadcrumb from './Breadcrumb.vue'

const appStore = useAppStore()
</script>

<template>
  <div class="navbar">
    <div class="navbar-left">
      <el-icon class="collapse-btn" @click="appStore.toggleSidebar()">
        <Fold v-if="!appStore.sidebarCollapsed" />
        <Expand v-else />
      </el-icon>
      <Breadcrumb />
    </div>
  </div>
</template>

<style scoped lang="scss">
.navbar {
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  border-bottom: 1px solid $border-lighter;
  padding: 0 16px;
  flex-shrink: 0;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: $text-regular;

  &:hover {
    color: $color-primary;
  }
}
</style>
