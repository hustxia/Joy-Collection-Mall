import request, { toPageData } from '@/utils/request'

// 商品SKU接口：mall_sku（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallSkuList(params) {
  return request({
    url: '/api/mall/mall_sku/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function exportMallSku(params) {
  return request({
    url: '/api/mall/mall_sku/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallSkuDetail(id) {
  return request({
    url: `/api/mall/mall_sku/${id}`,
    method: 'get'
  })
}
export function createMallSku(data) {
  return request({
    url: '/api/mall/mall_sku',
    method: 'post',
    data
  })
}
export function updateMallSku(id, data) {
  return request({
    url: '/api/mall/mall_sku',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallSku(id) {
  return request({
    url: `/api/mall/mall_sku/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====
