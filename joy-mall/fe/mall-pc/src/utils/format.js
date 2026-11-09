/** 分 → 元，保留两位小数（后端金额字段是 Long，序列化后可能是字符串） */
export function fenToYuan(fen) {
  const n = Number(fen)
  if (!Number.isFinite(n)) return '0.00'
  return (n / 100).toFixed(2)
}

/**
 * specJson → "颜色:曜石黑 / 存储:12G+256G"
 * 兼容三种形态：对象映射 {"颜色":"黑"}、数组 [{"k":"颜色","v":"黑"}]、普通文本
 */
export function specText(specJson) {
  if (!specJson) return ''
  try {
    const parsed = JSON.parse(specJson)
    if (Array.isArray(parsed)) {
      return parsed
        .map(i => `${i.k || i.key || i.name || ''}:${i.v || i.value || ''}`)
        .filter(s => s !== ':')
        .join(' / ')
    }
    if (parsed && typeof parsed === 'object') {
      return Object.entries(parsed).map(([k, v]) => `${k}:${v}`).join(' / ')
    }
    return String(specJson)
  } catch (e) {
    return String(specJson)
  }
}

export const ORDER_STATUS = {
  10: { text: '待付款', type: 'danger' },
  20: { text: '待发货', type: 'warning' },
  30: { text: '已发货', type: 'primary' },
  40: { text: '已完成', type: 'success' },
  50: { text: '已取消', type: 'info' },
  60: { text: '已关闭', type: 'info' }
}

export function orderStatusText(status) {
  const s = ORDER_STATUS[Number(status)]
  return s ? s.text : '未知'
}

export function orderStatusType(status) {
  const s = ORDER_STATUS[Number(status)]
  return s ? s.type : 'info'
}

/** 日期时间截断到分钟：2026-10-29 10:52:42 → 2026-10-29 10:52 */
export function shortTime(value) {
  if (!value) return '—'
  const text = String(value).replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

/** 售后状态（与 H5 format.js 同源，双端独立实现） */
export const REFUND_STATUS = {
  1: { text: '待审核', type: 'warning' },
  2: { text: '已同意', type: 'primary' },
  3: { text: '已拒绝', type: 'danger' },
  4: { text: '已退款', type: 'success' },
  5: { text: '已关闭', type: 'info' },
  6: { text: '买家已寄回', type: 'primary' },
  7: { text: '商家已收货', type: 'primary' },
  8: { text: '已换货', type: 'success' }
}

export function refundStatusText(status) {
  const s = REFUND_STATUS[Number(status)]
  return s ? s.text : '未知'
}

export function refundStatusType(status) {
  const s = REFUND_STATUS[Number(status)]
  return s ? s.type : 'info'
}

export const REFUND_TYPE = { 1: '仅退款', 2: '退货退款', 3: '换货', 4: '部分退款' }

export function refundTypeText(type) {
  return REFUND_TYPE[Number(type)] || '未知'
}

/**
 * 缩略图 URL：仅对站内图片源（/img、/uploads）走统一 /thumb 端点缩放，降低列表/卡片首屏体积；
 * 详情页保持原图（不调用本函数）。非站内源（http 外链等）原样返回。
 * @param {string} src 原图相对路径，如 /img/goods/x.png
 * @param {number} w 目标宽度，默认 300
 */
export function thumb(src, w = 300) {
  if (!src) return src
  if (src.startsWith('/img/') || src.startsWith('/uploads/')) {
    return `/thumb?src=${encodeURIComponent(src)}&w=${w}`
  }
  return src
}
