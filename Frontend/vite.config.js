import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 設定。
 *
 * proxy 是三層式架構在開發期的接點：
 * 前端開發伺服器（Web Server 角色）把 /api 請求轉發到
 * Spring Boot（Application Server 角色，預設 8080 埠）。
 *
 * 正式環境改由 Web Server（如 Nginx）提供 `npm run build` 產出的靜態檔案，
 * 並將 /api 反向代理至 Application Server。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    // 沿用 Vite 預設埠。不設 strictPort：埠被佔用時讓 Vite 自動遞增，
    // 比直接啟動失敗友善——啟動後看終端機印出的實際網址即可。
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
