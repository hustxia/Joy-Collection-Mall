import request, { toPageData } from '@/utils/request'

// 轮播图接口：mall_banner（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallBannerList(params) {
  return request({
    url: '/api/mall/mall_banner/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function exportMallBanner(params) {
  return request({
    url: '/api/mall/mall_banner/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallBannerDetail(id) {
  return request({
    url: `/api/mall/mall_banner/${id}`,
    method: 'get'
  })
}
export function createMallBanner(data) {
  return request({
    url: '/api/mall/mall_banner',
    method: 'post',
    data
  })
}
export function updateMallBanner(id, data) {
  return request({
    url: '/api/mall/mall_banner',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallBanner(id) {
  return request({
    url: `/api/mall/mall_banner/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====