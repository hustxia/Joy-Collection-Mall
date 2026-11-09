import request from '@/utils/request'

/** 客服工单（V14 REQ-P1-08）：会员提交 → 后台回复 → 超 24 小时未首响标记超时 */
export function pageTicket(data) {
  return request.post('/mall_ticket/page', data)
}

export function getTicket(id) {
  return request.get(`/mall_ticket/${id}`)
}

export function replyTicket(id, content) {
  return request.post(`/mall_ticket/${id}/reply`, { content })
}

export function ticketStatus(id, status) {
  return request.put(`/mall_ticket/${id}/status`, { id, status })
}

export function ticketStat() {
  return request.get('/mall_ticket/stat')
}
