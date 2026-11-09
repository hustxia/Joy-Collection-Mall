import request, { toPageData } from '@/utils/request'

// 优惠券接口：mall_coupon（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallCouponList(params) {
  return request({
    url: '/api/mall/mall_coupon/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function exportMallCoupon(params) {
  return request({
    url: '/api/mall/mall_coupon/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallCouponDetail(id) {
  return request({
    url: `/api/mall/mall_coupon/${id}`,
    method: 'get'
  })
}
export function createMallCoupon(data) {
  return request({
    url: '/api/mall/mall_coupon',
    method: 'post',
    data
  })
}
export function updateMallCoupon(id, data) {
  return request({
    url: '/api/mall/mall_coupon',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallCoupon(id) {
  return request({
    url: `/api/mall/mall_coupon/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====