import request from '@/utils/request'

// V16 在线 AI 客服：网关路由 /shop/ai-chat/**，devServer 把 /shop 直连 8080 不剥前缀
// （baseURL 已是 /shop，这里路径从 /ai-chat 开始）

/** 建会话 channel:'pc'|'h5'，source 可选 'float'|'goods_detail'|'order_detail' */
export const createSession = data => request.post('/ai-chat/session', data)

/** 发消息（同步返回，打字机效果由前端本地实现） */
export const sendMessage = data => request.post('/ai-chat/message', data)

/** 历史消息；silent 用于恢复失败时静默重建会话 */
export const getHistory = (sessionId, params) =>
  request.get(`/ai-chat/session/${sessionId}/history`, { params, silent: true })

/** 消息反馈 rating:'up'|'down' */
export const sendFeedback = data => request.post('/ai-chat/feedback', data)

/** 转人工 */
export const transferToHuman = sessionId => request.post('/ai-chat/transfer', { sessionId })
