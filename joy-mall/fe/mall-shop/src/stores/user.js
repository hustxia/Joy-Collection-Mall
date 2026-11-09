import { defineStore } from 'pinia'
import { login, register, logout, getProfile } from '@/api/auth'

const TOKEN_KEY = 'mall-shop-token'
const MEMBER_KEY = 'mall-shop-member'

// 会员登录态：token + member，持久化到 localStorage
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    member: JSON.parse(localStorage.getItem(MEMBER_KEY) || 'null'),
  }),
  getters: {
    isLogin: s => !!s.token,
  },
  actions: {
    setSession(token, member) {
      this.token = token || ''
      this.member = member || null
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(MEMBER_KEY, JSON.stringify(this.member))
    },
    async login(phone, password) {
      const data = await login({ phone, password })
      this.setSession(data.token, data.member)
    },
    async register(phone, password, nickname) {
      const data = await register({ phone, password, nickname })
      this.setSession(data.token, data.member)
    },
    async fetchProfile() {
      this.member = await getProfile()
      localStorage.setItem(MEMBER_KEY, JSON.stringify(this.member))
    },
    async logout() {
      try {
        await logout()
      } catch (e) {
        // 会话可能已失效，本地态照常清
      }
      this.token = ''
      this.member = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(MEMBER_KEY)
    },
  },
})
