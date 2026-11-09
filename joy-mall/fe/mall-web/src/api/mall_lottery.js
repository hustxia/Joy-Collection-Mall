import request from '@/utils/request'

/** 抽奖活动管理（V8） */
export function pageLottery(data) {
  return request.post('/mall_lottery/page', data)
}

/** 详情含奖品池：{ activity, prizes } */
export function getLottery(id) {
  return request.get(`/mall_lottery/${id}`)
}

export function saveLottery(data) {
  return data && data.id ? request.put('/mall_lottery', data) : request.post('/mall_lottery', data)
}

/** 删除活动连带逻辑删除奖品池 */
export function removeLottery(id) {
  return request.delete(`/mall_lottery/${id}`)
}

/** 奖品池整池保存：先按活动逻辑删再批量插 */
export function saveLotteryPrizes(activityId, prizes) {
  return request.post('/mall_lottery/prize-save', { activityId, prizes })
}
