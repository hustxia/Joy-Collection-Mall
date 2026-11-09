import axios from 'axios'
import { ElMessage as Message } from 'element-plus'

// 会员会话 key：与 H5（mall-shop-token）分开，避免同一浏览器里两端抢登录态
export const TOKEN_KEY = 'mall-pc-token'
export const MEMBER_KEY = 'mall-pc-member'

/** 清登录态并跳登录页（携带回跳地址） */
export function toLogin() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(MEMBER_KEY)
  const current = window.location.pathname + window.location.search
  if (!current.startsWith('/login')) {
    window.location.href = '/login?redirect=' + encodeURIComponent(current)
  }
}

// baseURL 用 /shop：devServer 已把 /shop 直连网关 8080 且不剥前缀（见 vue.config.js）
const request = axios.create({
  baseURL: '/shop',
  timeout: 15000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

request.interceptors.response.use(
  response => {
    const body = response.data
    if (body && body.code === 200) {
      return body.data
    }
    // 业务失败也是 HTTP 200 + body.code=400/401/403
    if (body && body.code === 401) {
      toLogin()
      return Promise.reject(new Error(body.msg || '登录已失效'))
    }
    const msg = (body && body.msg) || '请求失败'
    // silent: true 时由调用方自行处理错误（如 AI 客服历史恢复失败静默重建），不弹全局 Message
    if (!response.config.silent) {
      Message.error(msg)
    }
    return Promise.reject(new Error(msg))
  },
  error => {
    const status = error.response && error.response.status
    const data = error.response && error.response.data
    const msg = (data && data.msg) || error.message || '网络异常'
    if (status === 401 || (data && data.code === 401)) {
      toLogin()
    } else if (!(error.config && error.config.silent)) {
      Message.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request
