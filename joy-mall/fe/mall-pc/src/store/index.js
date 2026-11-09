import { reactive } from 'vue'
import { TOKEN_KEY, MEMBER_KEY } from '@/utils/request'
import * as authApi from '@/api/auth'
import { getCartList } from '@/api/cart'

// 轻量全局状态：项目未引入 Vuex/Pinia，用 reactive() 足够承载登录态与购物车角标
const state = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  member: JSON.parse(localStorage.getItem(MEMBER_KEY) || 'null'),
  cartCount: 0
})

export const store = {
  state,
  get isLogin() {
    return !!state.token
  },
  setSession(token, member) {
    state.token = token || ''
    state.member = member || null
    localStorage.setItem(TOKEN_KEY, state.token)
    localStorage.setItem(MEMBER_KEY, JSON.stringify(state.member))
    store.refreshCartCount()
  },
  clearSession() {
    state.token = ''
    state.member = null
    state.cartCount = 0
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(MEMBER_KEY)
  },
  async login(phone, password) {
    const data = await authApi.login({ phone, password })
    store.setSession(data.token, data.member)
    return data
  },
  async register(payload) {
    const data = await authApi.register(payload)
    store.setSession(data.token, data.member)
    return data
  },
  async logout() {
    try {
      await authApi.logout()
    } catch (e) {
      // 会话可能已失效，本地态照常清
    }
    store.clearSession()
  },
  /** 购物车角标：行数而非件数，和 H5 TabBar 口径一致 */
  async refreshCartCount() {
    if (!state.token) {
      state.cartCount = 0
      return 0
    }
    try {
      const rows = await getCartList()
      state.cartCount = (rows || []).length
    } catch (e) {
      state.cartCount = 0
    }
    return state.cartCount
  }
}

export default store
