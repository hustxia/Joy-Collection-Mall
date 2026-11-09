import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 2.x 移除了旧的 el-icon-xxx 字体方案，通过字符串 icon prop 会 resolveDynamicComponent 失败
// 这里全局注册旧名 → 新 icons-vue 组件的映射，兼容代码中 86 处 icon="el-icon-xxx" 写法
import {
  Download, Money, Search, Plus, User, Lock, Upload, Refresh, RefreshRight,
  SwitchButton, Ship, Tickets
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { thumb } from '@/utils/format'

const app = createApp(App)
app.use(router)
app.use(ElementPlus, { size: 'small' })

// 旧 el-icon-xxx 名 → icons-vue 新组件映射
const legacyIconMap = {
  'el-icon-download': Download,
  'el-icon-money': Money,
  'el-icon-search': Search,
  'el-icon-plus': Plus,
  'el-icon-user': User,
  'el-icon-lock': Lock,
  'el-icon-upload2': Upload,          // 旧 upload2 → 新 Upload（icons-vue 无 Upload2）
  'el-icon-send': Ship,              // 旧 send（发货） → Ship
  'el-icon-refresh': Refresh,
  'el-icon-refresh-right': RefreshRight,
  'el-icon-switch-button': SwitchButton,
  'el-icon-s-order': Tickets,          // 旧 s-order → Tickets（icons-vue 无 Order）
}
for (const [name, comp] of Object.entries(legacyIconMap)) {
  app.component(name, comp)
}
// 列表页模板里的 thumb(row.image, 80)：模块级 import 模板取不到，统一挂全局属性
app.config.globalProperties.thumb = thumb

// V6.4 按钮级权限：v-hasPermi="['demo_fee:add']"，命中任一即显示；超管 *:*:* 直通
app.directive('hasPermi', {
  mounted(el, binding) {
    let perms = []
    try { perms = JSON.parse(localStorage.getItem('perms') || '[]') } catch (e) { perms = [] }
    const need = binding.value || []
    const ok = perms.includes('*:*:*') || need.some(p => perms.includes(p)
      || perms.includes(p.replace(/:[^:]*$/, ':*')))
    if (!ok) {
      // Vue3 下 removeChild 会打断后续 diff（patch 时 parentNode 为 null），改隐藏
      el.style.display = 'none'
    }
  }
})

app.mount('#app')
