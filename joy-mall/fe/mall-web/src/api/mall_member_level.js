import request from '@/utils/request'

/** 会员等级（V13 REQ-P1-03）：成长值阈值 → 自动升级，折扣率与包邮次数在下单时生效 */
export function pageLevel(data) {
  return request.post('/mall_member_level/page', data)
}

export function levelOptions() {
  return request.get('/mall_member_level/options')
}

export function getLevel(id) {
  return request.get(`/mall_member_level/${id}`)
}

export function saveLevel(data) {
  return data && data.id ? request.put('/mall_member_level', data) : request.post('/mall_member_level', data)
}

export function removeLevel(id) {
  return request.delete(`/mall_member_level/${id}`)
}
