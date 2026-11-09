import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import store from './store'
import './assets/shop.css'
import { thumb } from '@/utils/format'

const app = createApp(App)
app.use(router)
app.use(ElementPlus)

// 模板里直接用 thumb(row.image) 的页面有 3+ 个；模块级 import 在模板取不到
// （模板只有 this.*），逐个挂 methods 容易漏 → 统一挂到全局属性上
app.config.globalProperties.thumb = thumb

// 全局价格格式化：分 → 元
app.config.globalProperties.$price = function (fen) {
  if (fen === null || fen === undefined) return '--'
  return (fen / 100).toFixed(2)
}

app.mount('#app')
