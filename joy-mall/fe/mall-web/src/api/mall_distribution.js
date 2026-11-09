import request from '@/utils/request'

/* ── 分销员 ─────────────────────────────── */
export function pageDistributor(data) {
  return request.post('/mall_distributor/page', data)
}

/** 审核：{ id, pass, rejectReason } */
export function auditDistributor(data) {
  return request.post('/mall_distributor/audit', data)
}

export function enableDistributor(id) {
  return request.post(`/mall_distributor/enable/${id}`)
}

export function disableDistributor(id) {
  return request.post(`/mall_distributor/disable/${id}`)
}

/* ── 佣金台账 ───────────────────────────── */
export function pageCommission(data) {
  return request.post('/mall_commission/page', data)
}

/** 手动结算：后端 @RequestParam，走 query 传参 */
export function settleCommission(params) {
  return request.post('/mall_commission/settle', null, { params })
}

/* ── 佣金规则 ───────────────────────────── */
export function pageCommissionRule(data) {
  return request.post('/mall_commission_rule/page', data)
}

export function saveCommissionRule(data) {
  return request.post('/mall_commission_rule/save', data)
}

/* ── 提现审核 ───────────────────────────── */
export function pageWithdrawal(data) {
  return request.post('/mall_withdrawal/page', data)
}

/** 审核提现：{ id, pass, remark } */
export function auditWithdrawal(data) {
  return request.post('/mall_withdrawal/audit', data)
}
