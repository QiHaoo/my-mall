import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // 自动导入 Vue / Vue Router / Pinia 的 API（ref, reactive, useRouter 等）
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts'
    }),
    // 注意：Element Plus 采用全量引入（main.ts 中 app.use(ElementPlus)），
    // 不使用按需引入 resolver，避免样式冲突
    Components({
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 全局注入 SCSS 变量，所有组件可直接使用 $color-primary 等
        additionalData: `@use "@/assets/styles/variables.scss" as *;`
      }
    }
  },
  server: {
    port: 5173, // 与网关 CORS 配置一致（allowed-origins: http://localhost:5173）
    open: true
  }
})
