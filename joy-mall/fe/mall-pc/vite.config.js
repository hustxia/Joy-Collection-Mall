import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 后端地址由环境变量决定，默认走网关 8080（单体 8431 已下线）。
// 换目标：MALL_API_TARGET=http://127.0.0.1:8202 npm run dev
// 网关 8080 按路径前缀分发到 8201~8206 六个服务，前端其它代码零改动。
const API_TARGET = process.env.MALL_API_TARGET || 'http://127.0.0.1:8080'

// 沙箱容器兼容：IDE 内置预览等容器用 sandbox iframe（源为 null）加载页面，
// ES 模块与 fetch 都会强制 CORS 校验，无 ACAO 头时模块脚本直接被拦 → 整页白屏。
const sandboxCors = () => ({
  name: 'sandbox-cors',
  configureServer(s) { s.middlewares.use((req, res, next) => { res.setHeader('Access-Control-Allow-Origin', '*'); next() }) },
  configurePreviewServer(s) { s.middlewares.use((req, res, next) => { res.setHeader('Access-Control-Allow-Origin', '*'); next() }) },
})

export default defineConfig({
  plugins: [vue(), sandboxCors()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 9432,
    host: true,
    proxy: {
      // 前台公开接口直连后端 /shop/**（白名单，无需登录）
      '/shop': { target: API_TARGET, changeOrigin: true },
      // 上传图片直链代理，不剥前缀（后端就是 /uploads/**）
      '/uploads': { target: API_TARGET, changeOrigin: true },
      // 打包内置静态商品图（/img/goods/**），直链同 /uploads
      '/img': { target: API_TARGET, changeOrigin: true },
      // V4 收尾 P1-2 缩略图端点，免鉴权直链同 /img、/uploads
      '/thumb': { target: API_TARGET, changeOrigin: true },
    },
  },
})
