import request, { toPageData } from '@/utils/request'

// V10 商家提现：分页 / 发起（即冻结余额）/ 审核（mall-merchant）
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

/** 分页查询提现单（merchantId / status 可选） */
export function getMallMerchantWithdrawalPage(params) {
  return request({
    url: '/api/mall/mall_merchant_withdrawal/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

/** 发起提现（平台代商家操作，申请即扣减可提现余额；余额不足 400） */
export function applyMallMerchantWithdrawal(data) {
  return request({
    url: '/api/mall/mall_merchant_withdrawal/apply',
    method: 'post',
    data
  })
}

/** 提现审核：pass=true 已打款 / false 拒绝并原路退回余额 */
export function auditMallMerchantWithdrawal(data) {
  return request({
    url: '/api/mall/mall_merchant_withdrawal/audit',
    method: 'post',
    data
  })
}
