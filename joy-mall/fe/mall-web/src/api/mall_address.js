import request, { toPageData } from '@/utils/request'

// 收货地址接口：mall_address（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallAddressList(params) {
  return request({
    url: '/api/mall/mall_address/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function exportMallAddress(params) {
  return request({
    url: '/api/mall/mall_address/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallAddressDetail(id) {
  return request({
    url: `/api/mall/mall_address/${id}`,
    method: 'get'
  })
}
export function createMallAddress(data) {
  return request({
    url: '/api/mall/mall_address',
    method: 'post',
    data
  })
}
export function updateMallAddress(id, data) {
  return request({
    url: '/api/mall/mall_address',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallAddress(id) {
  return request({
    url: `/api/mall/mall_address/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====