import request, { toPageData } from '@/utils/request'

// V10 商家结算单：分页 / 生成 / 明细下钻（mall-merchant）
function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

/** 分页查询结算单（merchantId / status 可选） */
export function getMallMerchantSettlementPage(params) {
  return request({
    url: '/api/mall/mall_merchant_settlement/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

/** 生成结算单：归集该商家待出账明细，无明细则 400 */
export function buildMallMerchantSettlement(data) {
  return request({
    url: '/api/mall/mall_merchant_settlement/build',
    method: 'post',
    data
  })
}

/** 结算单明细（订单级：实付 / 商家应收 / 平台抽成 / 退款冲减） */
export function getMallMerchantSettlementDetails(id) {
  return request({
    url: `/api/mall/mall_merchant_settlement/${id}/details`,
    method: 'get'
  })
}
