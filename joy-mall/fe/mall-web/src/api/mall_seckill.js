import request from '@/utils/request'

/** 秒杀活动管理（V8）：新增/修改时后端重算 availableStock */
export function pageSeckill(data) {
  return request.post('/mall_seckill/page', data)
}

export function getSeckill(id) {
  return request.get(`/mall_seckill/${id}`)
}

export function saveSeckill(data) {
  return data && data.id ? request.put('/mall_seckill', data) : request.post('/mall_seckill', data)
}

/** 结束活动（状态置 2，幂等） */
export function closeSeckill(id) {
  return request.post(`/mall_seckill/close/${id}`)
}

export function removeSeckill(id) {
  return request.delete(`/mall_seckill/${id}`)
}
