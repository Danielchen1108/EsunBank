import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 設定。
 *
 * proxy 是三層式架構（題目 §5）在開發期的接點：
 * 前端開發伺服器（Web Server 角色）把 /api 請求轉發到
 * Spring Boot（Application Server 角色，預設 8080 埠）。
 *
 * 正式環境改由 Web Server（如 Nginx）提供 `npm run build` 產出的靜態檔案，
 * 並將 /api 反向代理至 Application Server。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
