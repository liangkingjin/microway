import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 微途旅行前端（wtlx 原 39 个静态页面的 Vue3 重构工程）
// 部署目标：nginx 静态托管 http://localhost:8080（hash 路由，零 nginx 配置）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 业务后端：浏览器 /api/** → 直连 127.0.0.1:9528（rewrite 掉 /api 前缀，与原 nginx 反代行为一致）
      // 生产部署（dist 静态托管）时由 nginx 完成同样的反代
      '/api': {
        target: 'http://localhost:9528',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    chunkSizeWarningLimit: 1500
  }
})
