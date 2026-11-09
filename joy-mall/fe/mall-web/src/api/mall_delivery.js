import request, { toPageData } from '@/utils/request'

// V12 发货单：台账 / 单笔发货（支持部分发货）/ 批量导入

/** 分页查询发货单（GET：后端用 @RequestParam 接收） */
export function getMallDeliveryPage(params) {
  const { page, size, ...query } = params || {}
  return request({
    url: '/api/mall/mall_delivery/page',
    method: 'get',
    params: { pageNum: page || 1, pageSize: size || 10, ...query }
  }).then(toPageData)
}

/** 发货单详情（包裹明细） */
export function getMallDeliveryDetail(id) {
  return request({
    url: `/api/mall/mall_delivery/${id}`,
    method: 'get'
  })
}

/** 发货：items 为空表示发剩余全部 */
export function shipMallDelivery(data) {
  return request({
    url: '/api/mall/mall_delivery/ship',
    method: 'post',
    data
  })
}

/** 批量发货：逐行校验，返回成功数与错误报告 */
export function batchMallDelivery(rows) {
  return request({
    url: '/api/mall/mall_delivery/batch',
    method: 'post',
    data: { rows }
  })
}
