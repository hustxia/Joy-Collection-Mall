import request from '@/utils/request'

// 会员积分（V7）：需会员登录
export const getPointBalance = () => request.get('/shop/point/balance')
export const getPointLogs = params => request.get('/shop/point/logs', { params })
