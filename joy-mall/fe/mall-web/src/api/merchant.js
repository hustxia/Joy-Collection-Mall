import request from '@/utils/request'

/**
 * 商家后台接口（V11）。
 *
 * 约定：所有路径以 /merchant-admin 开头，request.js 会自动改用 Merchant-Token；
 * 后端从令牌里取 merchantId，前端传的任何 merchantId 都会被服务端覆盖，
 * 所以这里不提供「切换店铺」参数——越权只能靠改令牌，改参数没用。
 */

// ── 登录 ──────────────────────────────────────────────
export function merchantLogin(data) {
  return request.post('/merchant-admin/auth/login', data)
}

export function merchantLogout() {
  return request.post('/merchant-admin/auth/logout')
}

export function merchantProfile() {
  return request.get('/merchant-admin/auth/profile')
}

// ── 经营概览 ──────────────────────────────────────────
export function overview() {
  return request.get('/merchant-admin/overview')
}

// ── 商品 ──────────────────────────────────────────────
export function goodsPage(params) {
  return request.get('/merchant-admin/goods/page', { params })
}

export function goodsDetail(id) {
  return request.get(`/merchant-admin/goods/${id}`)
}

/** 有 id 走修改，无 id 走新增（后端 PUT 全量覆盖 SKU） */
export function goodsSave(data) {
  return data && data.id
    ? request.put('/merchant-admin/goods', data)
    : request.post('/merchant-admin/goods', data)
}

export function goodsStatus(id, status) {
  return request.put('/merchant-admin/goods/status', { id, status })
}

export function skuStock(skuId, stock) {
  return request.put('/merchant-admin/sku/stock', { skuId, stock })
}

// ── 订单 ──────────────────────────────────────────────
export function orderPage(params) {
  return request.get('/merchant-admin/order/page', { params })
}

export function orderDetail(id) {
  return request.get(`/merchant-admin/order/${id}`)
}

/** items 为空 = 把剩余未发数量全部发出（部分发货传 [{orderItemId, quantity}]） */
export function deliver(id, data) {
  return request.post(`/merchant-admin/order/${id}/deliver`, data || {})
}

// ── 售后 ──────────────────────────────────────────────
export function refundPage(params) {
  return request.get('/merchant-admin/refund/page', { params })
}

export function refundAudit(data) {
  return request.post('/merchant-admin/refund/audit', data)
}

// ── 资金 ──────────────────────────────────────────────
export function settlementPage(params) {
  return request.get('/merchant-admin/settlement/page', { params })
}

export function withdrawalPage(params) {
  return request.get('/merchant-admin/withdrawal/page', { params })
}

export function withdrawalApply(data) {
  return request.post('/merchant-admin/withdrawal/apply', data)
}

/** 对账单导出：后端返回 CSV（UTF-8 BOM），responseType 必须是 blob 否则中文乱码 */
export function billExport(params) {
  return request.get('/merchant-admin/bill/export', { params, responseType: 'blob' })
}
