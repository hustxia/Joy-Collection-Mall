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
