import request, { toPageData } from '@/utils/request'

// V10 商户入驻：商家台账 / 审核 / 启停 / 资料维护（mall-merchant，经网关 /api/mall）
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

/** 分页查询商家（keyword 命中店铺名/商家编号/联系人/执照主体） */
export function getMallMerchantPage(params) {
  return request({
    url: '/api/mall/mall_merchant/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

/** 启用中店铺下拉（结算/提现/发品选商家） */
export function getMallMerchantOptions() {
  return request({
    url: '/api/mall/mall_merchant/options',
    method: 'get'
  })
}

/** 商家详情（merchant + 审核流水 logs 时间线） */
export function getMallMerchantDetail(id) {
  return request({
    url: `/api/mall/mall_merchant/${id}`,
    method: 'get'
  })
}

/** 提交入驻申请（平台代商家提交） */
export function applyMallMerchant(data) {
  return request({
    url: '/api/mall/mall_merchant/apply',
    method: 'post',
    data
  })
}

/** 入驻审核：pass=true 通过 / false 驳回（驳回必填 rejectReason） */
export function auditMallMerchant(data) {
  return request({
    url: '/api/mall/mall_merchant/audit',
    method: 'post',
    data
  })
}

/** 店铺启用 / 停用：enable=true 启用 */
export function changeMallMerchantStatus(data) {
  return request({
    url: '/api/mall/mall_merchant/status',
    method: 'post',
    data
  })
}

/** 修改店铺资料与抽成费率 */
export function updateMallMerchant(data) {
  return request({
    url: '/api/mall/mall_merchant',
    method: 'put',
    data
  })
}
