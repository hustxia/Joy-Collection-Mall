# mall 前端（mall-web）

Vue 2.7 + Element UI，由 codegen-server 脚手架生成。

## 快速开始

```bash
npm install
npm run serve
```

访问 `http://127.0.0.1:9431`，`/dev-api` 已代理到后端 `http://127.0.0.1:8431`。

## 已内置

- `vue.config.js` 的 `historyApiFallback: true`（否则刷新页面 404）
- `utils/request.js` 统一注入 token、按 `code` 判成败、401 回登录页
- `layout/index.vue` 侧边栏骨架
