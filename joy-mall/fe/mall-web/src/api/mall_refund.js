import request, { toPageData } from '@/utils/request'

// 售后退款单接口：mall_refund（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallRefundList(params) {
  return request({
    url: '/api/mall/mall_refund/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function exportMallRefund(params) {
  return request({
    url: '/api/mall/mall_refund/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallRefundDetail(id) {
  return request({
    url: `/api/mall/mall_refund/${id}`,
    method: 'get'
  })
}
export function createMallRefund(data) {
  return request({
    url: '/api/mall/mall_refund',
    method: 'post',
    data
  })
}
export function updateMallRefund(id, data) {
  return request({
    url: '/api/mall/mall_refund',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallRefund(id) {
  return request({
    url: `/api/mall/mall_refund/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====

// V4 售后闭环：审核（仅待审核）与执行退款（仅已同意）
export function auditMallRefund(data) {
  return request({
    url: '/api/mall/mall_refund/audit',
    method: 'post',
    data
  })
}
export function refundMallRefund(id) {
  return request({
    url: '/api/mall/mall_refund/refund',
    method: 'post',
    data: { id }
  })
}
// ===== V15 售后扩展（REQ-P1-06）=====
// 换货：商家收货确认（仅「买家已寄回」）
export function confirmReceipt(id) {
  return request({ url: '/api/mall/mall_refund/confirm-receipt', method: 'post', data: { id } })
}
// 换货：发出换货（仅「商家已收货」）
export function sendExchange(id) {
  return request({ url: '/api/mall/mall_refund/send-exchange', method: 'post', data: { id } })
}
// 平台仲裁队列（商家超 48 小时未处理，惰性判定）
export function arbitrationPage(params) {
  return request({
    url: '/api/mall/mall_refund/arbitration/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
// 平台仲裁裁决：forceRefund=true 强制退款（走退款主流程终态），false 驳回
export function arbitrationVerdict(data) {
  return request({ url: '/api/mall/mall_refund/arbitration/verdict', method: 'post', data })
}
