/**
 * 缩略图 URL：仅对站内图片源（/img、/uploads）走统一 /thumb 端点缩放，降低列表/卡片首屏体积；
 * 详情页保持原图（不调用本函数）。非站内源（http 外链等）原样返回。
 * @param {string} src 原图相对路径，如 /img/goods/x.png
 * @param {number} w 目标宽度，默认 300
 */
export function thumb(src, w = 300) {
  if (!src) return src
  if (src.startsWith('/img/') || src.startsWith('/uploads/')) {
    return '/thumb?src=' + encodeURIComponent(src) + '&w=' + w
  }
  return src
}

/**
 * 分 → 元字符串（后台金额统一按「分」存储，展示两位小数）。
 * 不参与任何计算：需要加减请在后端算完再传，避免浮点误差。
 * @param {number|string} fen 金额（分）
 */
export function fenToYuan(fen) {
  const n = Number(fen)
  if (!isFinite(n)) return '0.00'
  return (n / 100).toFixed(2)
}

/**
 * 元输入 → 分（整数）。表单里运营填元，落库一律分：
 * Math.round(x * 100) 而不是 parseInt(x * 100)，否则 8.29 会变成 828。
 * @param {number|string} yuan 金额（元）
 */
export function yuanToFen(yuan) {
  const n = Number(yuan)
  if (!isFinite(n)) return 0
  return Math.round(n * 100)
}
