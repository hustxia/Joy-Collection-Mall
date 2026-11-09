import request, { toPageData } from '@/utils/request'

// 商品类目接口：mall_category（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallCategoryList(params) {
  return request({
    url: '/api/mall/mall_category/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function getMallCategoryTree() {
  return request({
    url: '/api/mall/mall_category/tree',
    method: 'get'
  })
}
export function exportMallCategory(params) {
  return request({
    url: '/api/mall/mall_category/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallCategoryDetail(id) {
  return request({
    url: `/api/mall/mall_category/${id}`,
    method: 'get'
  })
}
export function createMallCategory(data) {
  return request({
    url: '/api/mall/mall_category',
    method: 'post',
    data
  })
}
export function updateMallCategory(id, data) {
  return request({
    url: '/api/mall/mall_category',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallCategory(id) {
  return request({
    url: `/api/mall/mall_category/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====