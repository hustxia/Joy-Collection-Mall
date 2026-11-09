import axios from 'axios'
import { showToast } from 'vant'

const TOKEN_KEY = 'mall-shop-token'
const MEMBER_KEY = 'mall-shop-member'

/** 清登录态并跳登录页（携带来源页，登录后回跳） */
function toLogin() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(MEMBER_KEY)
  const redirect = window.location.pathname + window.location.search
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login?redirect=' + encodeURIComponent(redirect)
  }
}

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截：自动带会员 token
request.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一解 {code,msg,data}；401 跳登录；其余 toast msg 并 reject
request.interceptors.response.use(
  res => {
    const body = res.data
    if (body && body.code === 200) {
      return body.data
    }
    if (body && body.code === 401) {
      toLogin()
      return Promise.reject(new Error(body.msg || '未登录'))
    }
    const msg = (body && body.msg) || '请求失败'
    // silent: true 时由调用方自行处理错误（如 AI 客服历史恢复失败静默重建），不弹全局 toast
    if (!res.config.silent) {
      showToast(msg)
    }
    return Promise.reject(new Error(msg))
  },
  err => {
    // 后端 401/403 以 HTTP 状态码返回，body 仍为 {code,msg,data}
    const status = err.response && err.response.status
    const msg = (err.response && err.response.data && err.response.data.msg) || err.message || '网络异常'
    if (status === 401 || (err.response && err.response.data && err.response.data.code === 401)) {
      toLogin()
    } else if (!(err.config && err.config.silent)) {
      showToast(msg)
    }
    return Promise.reject(err)
  },
)

export default request
