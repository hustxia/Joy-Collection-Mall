import request from '@/utils/request'

export const confirmOrder = data => request.post('/order/confirm', data)
export const submitOrder = data => request.post('/order/submit', data)
export const getOrderPage = params => request.get('/order/page', { params })
export const getOrderDetail = id => request.get(`/order/${id}`)
export const cancelOrder = orderId => request.post('/order/cancel', { orderId })
export const confirmReceipt = orderId => request.post('/order/confirm-receipt', { orderId })
