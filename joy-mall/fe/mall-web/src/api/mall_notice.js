import request, { toPageData } from '@/utils/request'

// 站内消息台账（V7，只读）：经网关 /api/mall → /mall_notice（mall-member）
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

export function getMallNoticePage(params) {
  return request({
    url: '/api/mall/mall_notice/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}
