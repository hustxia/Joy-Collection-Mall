import request from '@/utils/request'

/** 消息触达（V14 REQ-P1-09）：短信/站内信双通道，失败重试后降级站内信并打 degraded 标记 */
export function pageMessageLog(params) {
  return request.get('/mall_message_log/page', { params })
}

export function pageTemplate(params) {
  return request.get('/mall_message_log/template/page', { params })
}

export function saveTemplate(data) {
  return request.post('/mall_message_log/template', data)
}
