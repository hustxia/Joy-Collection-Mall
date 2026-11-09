import request, { toPageData } from '@/utils/request'

// 订单流转日志接口：mall_order_log（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallOrderLogList(params) {
  return request({
    url: '/api/mall/mall_order_log/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function exportMallOrderLog(params) {
  return request({
    url: '/api/mall/mall_order_log/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallOrderLogDetail(id) {
  return request({
    url: `/api/mall/mall_order_log/${id}`,
    method: 'get'
  })
}
export function createMallOrderLog(data) {
  return request({
    url: '/api/mall/mall_order_log',
    method: 'post',
    data
  })
}
export function updateMallOrderLog(id, data) {
  return request({
    url: '/api/mall/mall_order_log',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallOrderLog(id) {
  return request({
    url: `/api/mall/mall_order_log/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====