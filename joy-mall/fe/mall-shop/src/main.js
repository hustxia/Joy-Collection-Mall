import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
// Vant 4 的 Lazyload 是独立插件，不在默认导出里；不注册则 van-image 的 lazy-load
// 永远不赋值 src（页面无 404、无报错，img.src=null 全是破图）
import { Lazyload } from 'vant/es/lazyload'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'
import './styles/index.css'
import { thumb } from '@/utils/format'

const app = createApp(App)
// 模板里直接用 thumb(src, w) 的页面/组件有 5+ 个；Vue 3 单文件组件里模块级 import 在模板取不到，
// 统一挂到 globalProperties（商品卡、购物车、订单、售后凭证都依赖它出缩略图）
app.config.globalProperties.thumb = thumb
app.use(createPinia())
app.use(router)
app.use(Vant)
app.use(Lazyload)
app.mount('#app')
