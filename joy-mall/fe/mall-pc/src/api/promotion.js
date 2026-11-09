import request from '@/utils/request'

// 促销活动（V13/V15）：商品可用活动 + 试算（baseURL 已含 /shop）
// available 匿名可读（商品卡/详情页未登录也要能看活动角标），失败静默返回空数组

// 模块级缓存：同一商品只拉一次，列表/首页/详情复用
const cache = new Map()

export function getGoodsPromotions(goodsId) {
  const key = String(goodsId)
  if (!cache.has(key)) {
    cache.set(
      key,
      request
        .get('/promotion/available', { params: { goodsId } })
        .catch(() => [])
    )
  }
  return cache.get(key)
}

// 促销试算（需登录）：items = [{ skuId, goodsId, merchantId, price(分), quantity }]
export const calculatePromotion = data => request.post('/promotion/calculate', data)

/**
 * 从可用活动里挑一个做角标：限时折扣优先（有确定活动价），其余取第一个
 */
export function pickBadge(promos) {
  if (!promos || !promos.length) return null
  return promos.find(p => Number(p.type) === 3) || promos[0] || null
}

/** 限时折扣的活动价（分）：单价 × 折扣率，四舍五入到分 */
export function promoPrice(priceFen, discountRate) {
  const rate = Number(discountRate)
  if (!Number.isFinite(rate) || rate <= 0 || rate >= 100) return null
  const n = Number(priceFen)
  if (!Number.isFinite(n)) return null
  return Math.round((n * rate) / 100)
}

/** 剩余秒数 → "HH:mm:ss" */
export function countdownText(seconds) {
  const left = Math.max(0, Math.floor(Number(seconds) || 0))
  const h = String(Math.floor(left / 3600)).padStart(2, '0')
  const m = String(Math.floor((left % 3600) / 60)).padStart(2, '0')
  const s = String(left % 60).padStart(2, '0')
  return `${h}:${m}:${s}`
}
