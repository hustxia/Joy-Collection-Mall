import request from '@/utils/request'

// V12 发票：申请开票 / 我的发票 / 按订单查（null 表示未申请）
export const applyInvoice = data => request.post('/invoice/apply', data)
export const getInvoicePage = params => request.get('/invoice/page', { params })
export const getInvoiceByOrder = orderId => request.get(`/invoice/order/${orderId}`)
