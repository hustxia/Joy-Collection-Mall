import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 后端地址由环境变量决定，默认走网关 8080（单体 8431 已下线）。
// 换目标：MALL_API_TARGET=http://127.0.0.1:8204 npm run dev
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
    port: 9531,
    host: true,
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true,
        rewrite: p => p.replace(/^\/api/, ''),
      },
      // 商品图直链两段必须单独代理：只代理 /api 时 vite 会拿 index.html 兜底返回 200+text/html，
      // 页面不报 404 但所有 <img> 都是破图（naturalWidth=0），排查时极易误判为数据问题
      '/img': { target: API_TARGET, changeOrigin: true },
      '/uploads': { target: API_TARGET, changeOrigin: true },
      // V4 收尾 P1-2 缩略图端点，免鉴权直链同 /img、/uploads
      '/thumb': { target: API_TARGET, changeOrigin: true },
    },
  },
})
