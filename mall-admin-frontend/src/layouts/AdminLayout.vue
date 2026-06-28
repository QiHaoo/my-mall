<script setup lang="ts">
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
import AppMain from './components/AppMain.vue'
</script>

<template>
  <div class="admin-layout">
    <Sidebar />
    <div class="main-container" :class="{ collapsed: false }">
      <Navbar />
      <AppMain />
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  display: flex;
  height: 100%;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
</style>
