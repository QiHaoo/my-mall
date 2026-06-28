<script setup lang="ts">
</script>

<template>
  <div class="app-main">
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

<style scoped lang="scss">
.app-main {
  flex: 1;
  overflow-y: auto;
  background-color: $bg-page;
}

/* 路由切换淡入过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
