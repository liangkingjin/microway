import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 微途旅行前端（wtlx 原 39 个静态页面的 Vue3 重构工程）
// 部署目标：nginx 静态托管 http://localhost:8080（hash 路由，零 nginx 配置）
export default defineConfig({
  plugins: [
    vue({
      template: {
        // /imgs/** 是"运行时资源"：dev 走下面的 proxy 由后端提供，生产由 nginx 直出，
        // 它们不是可打包的模块。而 @vitejs/plugin-vue 默认 includeAbsolute: true，会把模板里
        // 静态的 <img src="/imgs/xxx.png"> 改写成 `import _imports_0 from "/imgs/xxx.png?import"`；
        // 该请求会被 proxy 转给后端，拿到的是 Content-Type: image/png，
        // 浏览器按"模块脚本必须是 JS"做 MIME 校验直接拒绝 —— 于是**引用它的整个懒加载路由加载失败**，
        // 表现为点底部导航「我的」没反应（Profile.vue → ProfileBlogCard.vue 就是这么挂的），
        // 同理还有 /other-info、/login、/travel-list。这里关掉绝对路径的改写，让它保持普通 <img> 请求。
        transformAssetUrls: { includeAbsolute: false }
      }
    })
  ],
  server: {
    port: 5173,
    proxy: {
      // 业务后端：浏览器 /api/** → 直连 127.0.0.1:9528（rewrite 掉 /api 前缀，与原 nginx 反代行为一致）
      // 生产部署（dist 静态托管）时由 nginx 完成同样的反代
      '/api': {
        target: 'http://localhost:9528',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 上传落盘的图片：与后端静态映射保持一致（后端 MvcConfig 已映射 /imgs/**）
      // 开发环境不启动 nginx 时也能预览，生产仍由 nginx 直出
      '/imgs': { target: 'http://localhost:9528', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    chunkSizeWarningLimit: 1500
  }
})
