import request, { toPageData } from '@/utils/request'

// V12 运费模板：模板 + 地区规则（首件/首重 + 续件/续重 + 满额包邮）

function toPageQuery(params) {
  const { page, size, ...query } = params || {}
  return { pageNum: page || 1, pageSize: size || 10, ...query }
}

/** 分页查询模板 */
export function getMallFreightTemplatePage(params) {
  return request({
    url: '/api/mall/mall_freight_template/page',
    method: 'post',
    data: toPageQuery(params)
  }).then(toPageData)
}

/** 启用中的模板下拉（商品/店铺绑定用） */
export function getMallFreightTemplateOptions() {
  return request({
    url: '/api/mall/mall_freight_template/options',
    method: 'get'
  })
}

/** 模板详情（含地区规则） */
export function getMallFreightTemplateDetail(id) {
  return request({
    url: `/api/mall/mall_freight_template/${id}`,
    method: 'get'
  })
}

export function addMallFreightTemplate(data) {
  return request({
    url: '/api/mall/mall_freight_template',
    method: 'post',
    data
  })
}

export function updateMallFreightTemplate(data) {
  return request({
    url: '/api/mall/mall_freight_template',
    method: 'put',
    data
  })
}

/** 设为全局默认（同库互斥） */
export function setDefaultMallFreightTemplate(id) {
  return request({
    url: '/api/mall/mall_freight_template/default',
    method: 'post',
    data: { id }
  })
}

export function delMallFreightTemplate(id) {
  return request({
    url: `/api/mall/mall_freight_template/${id}`,
    method: 'delete'
  })
}
