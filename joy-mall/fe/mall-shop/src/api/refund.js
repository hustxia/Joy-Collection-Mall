import request from '@/utils/request'

// 售后（V4 P0-1）
export const applyRefund = data => request.post('/shop/refund/apply', data)
export const getRefundPage = params => request.get('/shop/refund/page', { params })
export const getRefundDetail = id => request.get(`/shop/refund/${id}`)
export const closeRefund = refundId => request.post('/shop/refund/close', { refundId })
export const getOrderRefundState = orderId => request.get(`/shop/refund/order/${orderId}`)
// 凭证图上传（multipart）
export const uploadEvidence = formData => request.post('/shop/upload', formData, {
  headers: { 'Content-Type': 'multipart/form-data' },
})

// ===== V15 售后扩展（REQ-P1-06）=====
// 申请换货（选换入 SKU，库存不立即回补）
export const applyExchange = data => request.post('/shop/refund/apply/exchange', data)
// 申请部分退款（按订单项选金额，累计不超过实付）
export const applyPartialRefund = data => request.post('/shop/refund/apply/part', data)
// 换货：买家寄回
export const shipRefundBack = refundId => request.post('/shop/refund/ship', { refundId })
// 平台介入（商家超 48 小时未处理）
export const interveneRefund = refundId => request.post('/shop/refund/intervene', { refundId })
