import request from '@/utils/request'

// 会员积分（V7）；baseURL 已含 /shop
export const getPointBalance = () => request.get('/point/balance')
export const getPointLogs = params => request.get('/point/logs', { params })
