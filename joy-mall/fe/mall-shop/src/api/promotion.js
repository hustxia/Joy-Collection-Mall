import request from '@/utils/request'

// 促销活动（V13/V15）：商品可用活动 + 试算
// available 匿名可读（商品卡/详情页未登录也要能看活动角标），失败静默返回空数组

// 模块级缓存：同一商品只拉一次，商品卡/首页/详情复用，避免一屏 N 个商品打 N 次接口
const cache = new Map()

export function getGoodsPromotions(goodsId) {
  const key = String(goodsId)
  if (!cache.has(key)) {
    cache.set(
      key,
      request
        .get('/shop/promotion/available', { params: { goodsId } })
        .catch(() => []),
    )
  }
  return cache.get(key)
}

// 促销试算（需登录）：items = [{ skuId, goodsId, merchantId, price(分), quantity }]
export const calculatePromotion = data => request.post('/shop/promotion/calculate', data)

/**
 * 从可用活动里挑一个做角标：限时折扣优先（有确定活动价），其余取第一个
 * 返回 { ruleText, type, discountRate } 或 null
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
