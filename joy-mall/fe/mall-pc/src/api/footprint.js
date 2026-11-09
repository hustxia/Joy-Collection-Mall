import request from '@/utils/request'

// 会员足迹（V4 P2-2）；baseURL 已含 /shop，身份由后端从 token 解析
export const recordFootprint = goodsId => request.post('/footprint/record', { goodsId: String(goodsId) })
export const getFootprintList = params => request.get('/footprint/list', { params })
export const clearFootprint = () => request.post('/footprint/clear')
