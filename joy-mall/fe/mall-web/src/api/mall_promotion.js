import request from '@/utils/request'

/** 促销活动（V13 REQ-P1-01）：满减/满折/限时折扣/第二件半价/套装/加价购 */
export function pagePromotion(data) {
  return request.post('/mall_promotion/page', data)
}

export function getPromotion(id) {
  return request.get(`/mall_promotion/${id}`)
}

/** 字典：类型/范围枚举，前端不硬编码数字含义 */
export function promotionDict() {
  return request.get('/mall_promotion/dict')
}

export function savePromotion(data) {
  return data && data.id ? request.put('/mall_promotion', data) : request.post('/mall_promotion', data)
}

export function promotionStatus(id, status) {
  return request.post('/mall_promotion/status', { id, status })
}

export function removePromotion(id) {
  return request.delete(`/mall_promotion/${id}`)
}
