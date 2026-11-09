import request from '@/utils/request'

// 订单
export const confirmOrder = data => request.post('/shop/order/confirm', data)
export const submitOrder = data => request.post('/shop/order/submit', data)
export const getOrderPage = params => request.get('/shop/order/page', { params })
export const getOrderDetail = id => request.get(`/shop/order/${id}`)
export const cancelOrder = orderId => request.post('/shop/order/cancel', { orderId })
export const confirmReceipt = orderId => request.post('/shop/order/confirm-receipt', { orderId })
