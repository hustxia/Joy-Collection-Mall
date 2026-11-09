import axios from 'axios'
import { ElMessage as Message } from 'element-plus'

const service = axios.create({
  baseURL: import.meta.env.VITE_BASE_API,
  timeout: 15000
})

service.interceptors.request.use(
  config => {
    const url = config.url || ''
    // V11：商家后台走独立令牌（Merchant-Token），与平台 Authorization 互不通用
    if (url.indexOf('/merchant-admin') === 0) {
      const merchantToken = localStorage.getItem('merchantToken')
      if (merchantToken) {
        config.headers['Merchant-Token'] = merchantToken
      }
      return config
    }
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  error => Promise.reject(error)
)

service.interceptors.response.use(
  response => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res.code !== 0 && res.code !== 200) {
      Message({ message: res.msg || '请求失败', type: 'error', duration: 5000 })
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    return res
  },
  error => {
    const resp = error.response
    const url = (error.config && error.config.url) || ''
    if (resp && resp.status === 401) {
      if (url.indexOf('/merchant-admin') === 0) {
        localStorage.removeItem('merchantToken')
        localStorage.removeItem('merchant')
        window.location.href = '/#/merchant/login'
        return Promise.reject(new Error('商家登录状态已失效'))
      }
      localStorage.removeItem('token')
      window.location.href = '/#/login'
      return Promise.reject(new Error('登录状态已失效'))
    }
    // V11：商家越权/停用一律 403，提示语直接透传后端文案（避免"操作失败"这种无信息提示）
    if (resp && resp.status === 403 && url.indexOf('/merchant-admin') === 0) {
      Message({ message: (resp.data && resp.data.msg) || '无权访问该资源', type: 'error', duration: 5000 })
      return Promise.reject(new Error((resp.data && resp.data.msg) || '无权访问该资源'))
    }
    Message({ message: (resp && resp.data && resp.data.msg) || error.message, type: 'error', duration: 5000 })
    return Promise.reject(error)
  }
)

// MyBatis-Plus Page {records,total} -> {list,total}
export function toPageData(res) {
  const page = res.data || {}
  return {
    code: res.code,
    msg: res.msg,
    data: { list: page.records || [], total: page.total || 0 }
  }
}

export default service
