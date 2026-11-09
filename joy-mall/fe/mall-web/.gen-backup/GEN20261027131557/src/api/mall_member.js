import request, { toPageData } from '@/utils/request'

// 会员接口：mall_member（经网关 /api/mall）
// <gen:begin key="api-fns">
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}
export function getMallMemberList(params) {
  return request({
    url: '/api/mall/mall_member/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
export function exportMallMember(params) {
  return request({
    url: '/api/mall/mall_member/export',
    method: 'post',
    data: params || {},
    responseType: 'blob'
  })
}
export function getMallMemberDetail(id) {
  return request({
    url: `/api/mall/mall_member/${id}`,
    method: 'get'
  })
}
export function createMallMember(data) {
  return request({
    url: '/api/mall/mall_member',
    method: 'post',
    data
  })
}
export function updateMallMember(id, data) {
  return request({
    url: '/api/mall/mall_member',
    method: 'put',
    data: { ...data, id }
  })
}
export function deleteMallMember(id) {
  return request({
    url: `/api/mall/mall_member/${id}`,
    method: 'delete'
  })
}
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====
