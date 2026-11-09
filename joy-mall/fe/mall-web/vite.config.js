import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 后端地址由环境变量决定，默认走网关 8080（单体 8431 已下线）。
// 换目标：MALL_API_TARGET=http://127.0.0.1:8201 npm run dev
// 网关 8080 会按路径前缀把请求分发到 8201~8206 六个服务，前端其它代码零改动。
const API_TARGET = process.env.MALL_API_TARGET || 'http://127.0.0.1:8080'

// 沙箱容器兼容：IDE 内置预览等容器用 sandbox iframe（源为 null）加载页面，
// ES 模块与 fetch 都会强制 CORS 校验，无 ACAO 头时模块脚本直接被拦 → 整页白屏。
// dev 与 preview 全部响应补 ACAO:*（本地演示服务，无安全敏感性）。
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
    port: 9431,
    host: true,
    proxy: {
      '/dev-api': {
        target: API_TARGET,
        changeOrigin: true,
        // http-proxy-middleware 多条 pathRewrite 不保证顺序执行的老问题不复存在；
        // vite rewrite 用一条正则等价替换 ^/dev-api(/api/mall)?
        rewrite: p => p.replace(/^\/dev-api(\/api\/mall)?/, ''),
      },
      // V6.6 文件能力包：图片静态直链代理到后端，不剥前缀（后端就是 /uploads/**）
      '/uploads': { target: API_TARGET, changeOrigin: true },
      // V4 收尾 P1-2 缩略图端点，免鉴权直链同 /uploads
      '/thumb': { target: API_TARGET, changeOrigin: true },
      // 打包内置静态商品图（/img/goods/**），后台列表直链同 /uploads
      '/img': { target: API_TARGET, changeOrigin: true },
    },
  },
  // 生产预览（npm run build 后 npx vite preview --port 9441）：
  // 生产包接口前缀是 /prod-api，代理规则与 dev 完全同构
  preview: {
    port: 9441,
    host: true,
    proxy: {
      '/prod-api': {
        target: API_TARGET,
        changeOrigin: true,
        rewrite: p => p.replace(/^\/prod-api(\/api\/mall)?/, ''),
      },
      '/uploads': { target: API_TARGET, changeOrigin: true },
      '/thumb': { target: API_TARGET, changeOrigin: true },
      '/img': { target: API_TARGET, changeOrigin: true },
    },
  },
})
