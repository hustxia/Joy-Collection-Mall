import request, { toPageData } from '@/utils/request'

// 商品评价管理（V7）：经网关 /api/mall → /mall_review（mall-goods）
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

export function getMallReviewPage(params) {
  return request({
    url: '/api/mall/mall_review/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

/** 后台回复评价 */
export function replyMallReview(id, reply) {
  return request({
    url: '/api/mall/mall_review/reply',
    method: 'post',
    data: { id, reply }
  })
}

/** 隐藏/恢复评价：status 0正常 1隐藏 */
export function hideMallReview(id, status) {
  return request({
    url: '/api/mall/mall_review/hide',
    method: 'post',
    data: { id, status }
  })
}
