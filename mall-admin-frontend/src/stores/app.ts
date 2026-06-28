import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 应用全局状态
 * - 侧边栏折叠状态，持久化到 localStorage
 */
export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('sidebarCollapsed', String(sidebarCollapsed.value))
  }

  return { sidebarCollapsed, toggleSidebar }
})
