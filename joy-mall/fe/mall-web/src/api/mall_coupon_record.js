import request, { toPageData } from '@/utils/request'

// 领券记录接口：mall_coupon_record（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallCouponRecordList(params) {
  return request({
    url: '/api/mall/mall_coupon_record/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function exportMallCouponRecord(params) {
  return request({
    url: '/api/mall/mall_coupon_record/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallCouponRecordDetail(id) {
  return request({
    url: `/api/mall/mall_coupon_record/${id}`,
    method: 'get'
  })
}
export function createMallCouponRecord(data) {
  return request({
    url: '/api/mall/mall_coupon_record',
    method: 'post',
    data
  })
}
export function updateMallCouponRecord(id, data) {
  return request({
    url: '/api/mall/mall_coupon_record',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallCouponRecord(id) {
  return request({
    url: `/api/mall/mall_coupon_record/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====