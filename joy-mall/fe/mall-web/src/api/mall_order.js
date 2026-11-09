import request, { toPageData } from '@/utils/request'

// 订单接口：mall_order（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallOrderList(params) {
  return request({
    url: '/api/mall/mall_order/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function getMallOrderWithChildren(id) {
  return request({
    url: `/api/mall/mall_order/${id}/with-children`,
    method: 'get'
  })
}

// payload = { master, ...各子表 {childVar}Rows }；子表字段 null=不动，[]=清空
export function saveMallOrderWithChildren(payload) {
  return request({
    url: '/api/mall/mall_order/save-with-children',
    method: 'post',
    data: payload
  })
}
export function exportMallOrder(params) {
  return request({
    url: '/api/mall/mall_order/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallOrderDetail(id) {
  return request({
    url: `/api/mall/mall_order/${id}`,
    method: 'get'
  })
}
export function createMallOrder(data) {
  return request({
    url: '/api/mall/mall_order',
    method: 'post',
    data
  })
}
export function updateMallOrder(id, data) {
  return request({
    url: '/api/mall/mall_order',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallOrder(id) {
  return request({
    url: `/api/mall/mall_order/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====

// V3 发货：快递公司 + 单号必填，仅待发货(20)可操作
export function deliverMallOrder(data) {
  return request({
    url: '/api/mall/mall_order/deliver',
    method: 'post',
    data
  })
}

// V4 P2-1 关闭订单（20→60，回补库存，不自动退款）
export function closeMallOrder(orderId) {
  return request({
    url: '/api/mall/mall_order/close',
    method: 'post',
    data: { orderId }
  })
}

// V4 P2-1 管理员备注
export function remarkMallOrder(orderId, remark) {
  return request({
    url: '/api/mall/mall_order/remark',
    method: 'post',
    data: { orderId, remark }
  })
}