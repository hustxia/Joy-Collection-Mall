import request, { toPageData } from '@/utils/request'

// V12 发票管理：开票队列 / 上传开票 / 红冲

function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

export function getMallInvoicePage(params) {
  return request({
    url: '/api/mall/mall_invoice/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

/** 开票：传电子发票文件地址 */
export function issueMallInvoice(id, fileUrl) {
  return request({
    url: '/api/mall/mall_invoice/issue',
    method: 'post',
    data: { id, fileUrl }
  })
}

/** 红冲：已开票置已红冲 */
export function redFlushMallInvoice(id, remark) {
  return request({
    url: '/api/mall/mall_invoice/red-flush',
    method: 'post',
    data: { id, remark }
  })
}
