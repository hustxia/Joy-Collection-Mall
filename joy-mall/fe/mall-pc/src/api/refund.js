import request from '@/utils/request'

// 售后（V4 P0-1）；baseURL 已含 /shop
export const applyRefund = data => request.post('/refund/apply', data)
export const getRefundPage = params => request.get('/refund/page', { params })
export const getRefundDetail = id => request.get(`/refund/${id}`)
export const closeRefund = refundId => request.post('/refund/close', { refundId })
export const getOrderRefundState = orderId => request.get(`/refund/order/${orderId}`)
export const uploadEvidence = formData => request.post('/upload', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})

// ===== V15 售后扩展（REQ-P1-06）=====
// 申请换货（选换入 SKU）
export const applyExchange = data => request.post('/refund/apply/exchange', data)
// 申请部分退款（按订单项选金额）
export const applyPartialRefund = data => request.post('/refund/apply/part', data)
// 换货：买家寄回
export const shipRefundBack = refundId => request.post('/refund/ship', { refundId })
// 平台介入（商家超 48 小时未处理）
export const interveneRefund = refundId => request.post('/refund/intervene', { refundId })
