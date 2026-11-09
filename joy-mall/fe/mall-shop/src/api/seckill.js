import request from '@/utils/request'

// 秒杀（V8/V15）：活动列表/详情/抢购
// scope: 'ongoing' 进行中（默认）/ 'upcoming' 即将开始
export const getSeckillActivities = scope =>
  request.get('/shop/seckill/activities', { params: scope ? { scope } : {} })
export const getSeckillDetail = id => request.get(`/shop/seckill/${id}`)
export const buySeckill = (id, data) => request.post(`/shop/seckill/${id}/buy`, data)
