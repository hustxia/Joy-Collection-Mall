import request, { toPageData } from '@/utils/request'

// V12 支付流水 + 对账

function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

/** 支付单分页（支付单号/订单号/渠道/状态） */
export function getMallPaymentPage(params) {
  return request({
    url: '/api/mall/mall_payment/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

/** 渠道可用性（configured=false 表示未配置商户资质） */
export function getMallPaymentChannels() {
  return request({
    url: '/api/mall/mall_payment/channels',
    method: 'get'
  })
}

/** 对账差异分页 */
export function getMallPayReconcilePage(params) {
  const { page, size, ...query } = params || {}
  return request({
    url: '/api/mall/mall_pay_reconcile/page',
    method: 'get',
    params: { pageNum: page || 1, pageSize: size || 10, ...query }
  }).then(toPageData)
}

/** 执行对账：不传账单时按本地镜像（沙箱）对账 */
export function runMallPayReconcile(data) {
  return request({
    url: '/api/mall/mall_pay_reconcile/run',
    method: 'post',
    data
  })
}

/** 处理差异：1 确认 / 2 忽略 */
export function handleMallPayReconcile(id, handleStatus, remark) {
  return request({
    url: '/api/mall/mall_pay_reconcile/handle',
    method: 'post',
    data: { id, handleStatus, remark }
  })
}
