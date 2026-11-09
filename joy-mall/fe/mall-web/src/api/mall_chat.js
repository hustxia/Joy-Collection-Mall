import request from '@/utils/request'

/** V16 AI 客服：会话记录 / FAQ 知识库 / 用量看板 */
export function pageChatSession(params) {
  return request.get('/mall_chat/session/page', { params })
}

export function getChatSession(id) {
  return request.get(`/mall_chat/session/${id}`)
}

export function pageFaq(params) {
  return request.get('/mall_chat/faq/page', { params })
}

export function addFaq(data) {
  return request.post('/mall_chat/faq', data)
}

export function updateFaq(data) {
  return request.put('/mall_chat/faq', data)
}

export function deleteFaq(id) {
  return request.delete(`/mall_chat/faq/${id}`)
}

export function chatStatOverview(params) {
  return request.get('/mall_chat/stat/overview', { params })
}
