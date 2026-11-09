import request, { toPageData } from '@/utils/request'

// 商品接口：mall_goods（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallGoodsList(params) {
  return request({
    url: '/api/mall/mall_goods/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function exportMallGoods(params) {
  return request({
    url: '/api/mall/mall_goods/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallGoodsDetail(id) {
  return request({
    url: `/api/mall/mall_goods/${id}`,
    method: 'get'
  })
}
export function createMallGoods(data) {
  return request({
    url: '/api/mall/mall_goods',
    method: 'post',
    data
  })
}
export function updateMallGoods(id, data) {
  return request({
    url: '/api/mall/mall_goods',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallGoods(id) {
  return request({
    url: `/api/mall/mall_goods/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====