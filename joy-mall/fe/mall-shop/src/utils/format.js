/** 分 → 元，保留两位小数 */
export function fenToYuan(fen) {
  const n = Number(fen)
  if (!Number.isFinite(n)) return '0.00'
  return (n / 100).toFixed(2)
}

/**
 * 后端时间串解析：LocalDateTime 序列化为 "yyyy-MM-dd HH:mm:ss"（空格分隔），
 * Safari/旧 WebView 对该格式 new Date 返回 Invalid Date，统一转成 ISO 的 T 分隔再解析
 */
export function parseTime(v) {
  if (!v) return null
  if (typeof v === 'string') return new Date(v.replace(' ', 'T'))
  return v
}

/**
 * specJson → "颜色:曜石黑 / 存储:12G+256G"
 * 兼容三种形态：对象映射 {"颜色":"黑"}、数组 [{"k":"颜色","v":"黑"}]、普通文本
 * 解析失败原样返回
 */
export function specText(specJson) {
  if (!specJson) return ''
  try {
    const parsed = JSON.parse(specJson)
    if (Array.isArray(parsed)) {
      return parsed
        .map(i => `${i.k ?? i.key ?? i.name ?? ''}:${i.v ?? i.value ?? ''}`)
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

const ORDER_STATUS = {
  10: '待付款',
  20: '待发货',
  30: '已发货',
  40: '已完成',
  50: '已取消',
  60: '已关闭',
}

export function orderStatusText(status) {
  return ORDER_STATUS[status] || '未知'
}

const REFUND_STATUS = {
  1: '待审核',
  2: '已同意',
  3: '已拒绝',
  4: '已退款',
  5: '已关闭',
  6: '买家已寄回',
  7: '商家已收货',
  8: '已换货',
}

export function refundStatusText(status) {
  return REFUND_STATUS[status] || '未知'
}

const REFUND_TYPE = {
  1: '仅退款',
  2: '退货退款',
  3: '换货',
  4: '部分退款',
}

export function refundTypeText(type) {
  return REFUND_TYPE[type] || '未知'
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

