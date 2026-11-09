import request, { toPageData } from '@/utils/request'

// 品牌接口：mall_brand（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallBrandList(params) {
  return request({
    url: '/api/mall/mall_brand/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function exportMallBrand(params) {
  return request({
    url: '/api/mall/mall_brand/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallBrandDetail(id) {
  return request({
    url: `/api/mall/mall_brand/${id}`,
    method: 'get'
  })
}
export function createMallBrand(data) {
  return request({
    url: '/api/mall/mall_brand',
    method: 'post',
    data
  })
}
export function updateMallBrand(id, data) {
  return request({
    url: '/api/mall/mall_brand',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallBrand(id) {
  return request({
    url: `/api/mall/mall_brand/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====