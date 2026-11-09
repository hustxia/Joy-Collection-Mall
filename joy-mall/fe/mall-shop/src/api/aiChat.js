import request from '@/utils/request'

// V16 在线 AI 客服：网关路由为 /shop/ai-chat/**，
// mall-shop 的 baseURL 是 /api 且 vite 代理会剥掉 /api 前缀，所以这里路径要带 /shop

/** 建会话 channel:'h5'，source 可选 'float'|'goods_detail'|'order_detail' */
export const createSession = data => request.post('/shop/ai-chat/session', data)

/** 发消息（同步返回，打字机效果由前端本地实现） */
export const sendMessage = data => request.post('/shop/ai-chat/message', data)

/** 历史消息；silent 用于恢复失败时静默重建会话 */
export const getHistory = (sessionId, params) =>
  request.get(`/shop/ai-chat/session/${sessionId}/history`, { params, silent: true })

/** 消息反馈 rating:'up'|'down' */
export const sendFeedback = data => request.post('/shop/ai-chat/feedback', data)

/** 转人工 */
export const transferToHuman = sessionId => request.post('/shop/ai-chat/transfer', { sessionId })
