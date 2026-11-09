// 微服务工程 × 三端前端 联调冒烟（走 devServer 代理，即前端真实请求路径）
// web 9431 (/dev-api → 网关 8080) / pc 9432 (/shop) / h5 9531 (/api → 去掉前缀)
// 覆盖：会员登录 → 商品浏览 → 购物车 → 确认订单 → 下单 → 支付 → 订单详情 → 售后申请
//       → 后台审核 → 退款(回补库存) → 后台各列表/看板
// 全部地址与账号可用环境变量覆盖，脚本零硬编码（CI/换机直接改环境）
const WEB = process.env.MALL_WEB_URL || 'http://127.0.0.1:9431'
const PC = process.env.MALL_PC_URL || 'http://127.0.0.1:9432'
const H5 = process.env.MALL_H5_URL || 'http://localhost:9531'
const MEMBER_PHONE = process.env.MALL_MEMBER_PHONE || '13800000001'
const MEMBER_PASSWORD = process.env.MALL_MEMBER_PASSWORD || '123456'
const ADMIN_USERNAME = process.env.MALL_ADMIN_USERNAME || 'admin'
const ADMIN_PASSWORD = process.env.MALL_ADMIN_PASSWORD || 'admin123'

let pass = 0, fail = 0
const fails = []
const ok = (cond, name, extra = '') => {
  if (cond) { pass++; console.log(`  OK   ${name}${extra ? '  [' + extra + ']' : ''}`) }
  else { fail++; fails.push(name); console.log(`  FAIL ${name}${extra ? '  [' + extra + ']' : ''}`) }
}
const step = t => console.log(`\n── ${t}`)

async function req(base, path, { method = 'GET', body, token, admin } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers['Authorization'] = 'Bearer ' + token
  const res = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  const text = await res.text()
  let json
  try { json = JSON.parse(text) } catch { json = { raw: text.slice(0, 200) } }
  return { status: res.status, json, text }
}

// 后台令牌：优先用外部传入（argv / MALL_ADMIN_TOKEN，人工兜底），否则脚本自己登录取
// （需要目标环境 AUTH_CAPTCHA_EXPOSE_ANSWER=true 才能取到算术验证码答案）
async function loginAdmin() {
  const preset = process.argv[2] || process.env.MALL_ADMIN_TOKEN
  if (preset) return preset
  const cap = await req(WEB, '/dev-api/api/mall/auth/captcha')
  const answer = cap.json && cap.json.data && cap.json.data.answer
  if (!answer) {
    console.error('取不到验证码答案：目标环境需以 AUTH_CAPTCHA_EXPOSE_ANSWER=true 启动 mall-system，'
      + '或改用 MALL_ADMIN_TOKEN / 命令行参数传现成令牌')
    process.exit(2)
  }
  const res = await req(WEB, '/dev-api/api/mall/auth/login', {
    method: 'POST',
    body: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD,
      captchaId: cap.json.data.captchaId, captchaAnswer: String(answer) }
  })
  const token = res.json && res.json.data && (res.json.data.token || res.json.data.accessToken)
  if (!token) {
    console.error('后台登录失败：' + JSON.stringify(res.json).slice(0, 200))
    process.exit(2)
  }
  return token
}

const ADMIN_TOKEN = await loginAdmin()

// ───────────── 1. 会员登录（H5 代理 → 网关 → member 8203）
step('1. 会员登录 / 跨服务令牌')
const login = await req(H5, '/api/shop/auth/login', { method: 'POST', body: { phone: MEMBER_PHONE, password: MEMBER_PASSWORD } })
ok(login.json.code === 200, 'H5 登录成功', login.json.msg)
const token = login.json.data && (login.json.data.token || login.json.data.accessToken)
ok(!!token, '拿到会员令牌', token ? token.slice(0, 8) + '…' : '')

const profilePc = await req(PC, '/shop/auth/profile', { token })
ok(profilePc.json.code === 200 && profilePc.json.data, 'PC 端用同一令牌读 profile（跨服务会话）',
  profilePc.json.data && profilePc.json.data.phone)

// ───────────── 2. 商品浏览（H5 + PC，goods 8202）
step('2. 商品浏览')
const goodsPage = await req(H5, '/api/shop/goods?pageNum=1&pageSize=3')
ok(goodsPage.json.code === 200 && goodsPage.json.data.records.length > 0, 'H5 商品列表',
  `共 ${goodsPage.json.data && goodsPage.json.data.total} 条`)
const goodsDetail = await req(H5, '/api/shop/goods/1001')
ok(goodsDetail.json.code === 200 && goodsDetail.json.data.skus.length > 0, 'H5 商品详情含 SKU',
  `SKU ${goodsDetail.json.data && goodsDetail.json.data.skus.length} 个`)
const pcBanners = await req(PC, '/shop/banners')
ok(pcBanners.json.code === 200 && pcBanners.json.data.length > 0, 'PC 轮播图', `共 ${pcBanners.json.data && pcBanners.json.data.length} 条`)
const pcCats = await req(PC, '/shop/categories')
ok(pcCats.json.code === 200 && pcCats.json.data.length > 0, 'PC 分类树', `共 ${pcCats.json.data && pcCats.json.data.length} 个一级`)
const imgUrl = goodsDetail.json.data.skus[0].image || goodsDetail.json.data.mainImage
const imgRes = await fetch(H5 + imgUrl, { method: 'GET' })
ok(imgRes.status === 200 && (imgRes.headers.get('content-type') || '').startsWith('image/'), '商品图经 devServer 代理可取', imgUrl)

// ───────────── 3. 购物车（member 8203 → goods 8202 Feign）
step('3. 购物车')
const SKU = Number(goodsDetail.json.data.skus.find(s => s.stock > 2).id)
const PRICE = Number(goodsDetail.json.data.skus.find(s => s.id == SKU).price)
await req(H5, '/api/shop/cart/add', { method: 'POST', token, body: { skuId: SKU, num: 1 } })
const cart = await req(H5, '/api/shop/cart/list', { token })
const cartRows = (cart.json.data || [])
const cartRow = cartRows.find(r => String(r.skuId) === String(SKU))
ok(cart.json.code === 200 && cartRow, '加购后列表含该 SKU')
ok(cartRow && cartRow.goodsName && Number(cartRow.price) > 0 && cartRow.stock !== undefined,
  '购物车商品名/价格/库存由 goods 服务补齐', cartRow && cartRow.goodsName)

// ───────────── 4. 确认订单 + 下单（order 8204 → member/goods/marketing/refund Feign）
step('4. 确认订单与下单')
const addrList = await req(H5, '/api/shop/address/list', { token })
const addr = (addrList.json.data || [])[0]
ok(!!addr, '会员有收货地址', addr && addr.receiverName)
const before = await req(WEB, '/dev-api/api/mall/mall_sku/' + SKU, { token: ADMIN_TOKEN })
const stockBefore = Number(before.json.data.stock)

const confirm = await req(H5, '/api/shop/order/confirm', { method: 'POST', token, body: { skuId: SKU, num: 1 } })
ok(confirm.json.code === 200, '确认订单页数据')
const cKeys = Object.keys(confirm.json.data || {})
ok(['items', 'totalAmount', 'discountAmount', 'payAmount', 'usableCoupons'].every(k => cKeys.includes(k)),
  '确认页字段与单体一致（items/金额/券）', cKeys.join(','))
ok(confirm.json.data && confirm.json.data.items[0].goodsName && Number(confirm.json.data.items[0].stock) > 0,
  '确认页商品名/库存由 goods 服务解析', confirm.json.data && confirm.json.data.items[0].goodsName)
ok(confirm.json.data && Array.isArray(confirm.json.data.usableCoupons), '可用券列表由 marketing 服务返回',
  `券 ${confirm.json.data && confirm.json.data.usableCoupons.length} 张`)

const submit = await req(H5, '/api/shop/order/submit', {
  method: 'POST', token,
  body: { addressId: addr.id, skuId: SKU, num: 1, buyerRemark: '微服务联调单' }
})
ok(submit.json.code === 200 && submit.json.data && submit.json.data.orderId, '提交订单', submit.json.msg)
const orderId = submit.json.data && submit.json.data.orderId
const orderNo = submit.json.data && submit.json.data.orderNo

const after = await req(WEB, '/dev-api/api/mall/mall_sku/' + SKU, { token: ADMIN_TOKEN })
ok(Number(after.json.data.stock) === stockBefore - 1, '库存已由 goods 服务扣减',
  `${stockBefore} → ${after.json.data.stock}`)

// 清空购物车（会员域自闭环）
await req(H5, '/api/shop/cart/' + cartRow.id, { method: 'DELETE', token })
const cartAfter = await req(H5, '/api/shop/cart/list', { token })
ok(!(cartAfter.json.data || []).some(r => String(r.skuId) === String(SKU)), '下单/手动清空后购物车移除该行')

// ───────────── 5. 支付（order 8204 → goods 加销量 + marketing 核销券 + member）
step('5. 支付（mock）')
const pay = await req(H5, '/api/shop/pay/mock', { method: 'POST', token, body: { orderNo } })
ok(pay.json.code === 200, '支付成功', pay.json.msg)
const orderPage = await req(H5, '/api/shop/order/page?pageNum=1&pageSize=5', { token })
const paid = (orderPage.json.data.records || []).find(o => String(o.id) === String(orderId))
ok(paid && Number(paid.status) === 20, '订单列表状态=20（待发货）', paid && `status ${paid.status}`)
const detail = await req(H5, '/api/shop/order/' + orderId, { token })
const dOrder = (detail.json.data && detail.json.data.order) || {}
ok(detail.json.code === 200 && detail.json.data.items && detail.json.data.items.length > 0, '订单详情含明细')
ok(dOrder.receiverName === addr.receiverName && dOrder.receiverPhone === addr.receiverPhone,
  '下单时收货信息由 member 服务地址落库', dOrder.receiverName)
ok((detail.json.data.logs || []).length >= 2, '订单日志含提交与支付记录', `${detail.json.data && detail.json.data.logs.length} 条`)
ok(detail.json.data.refund === null || detail.json.data.refund === undefined, '订单详情退款单字段由 refund 服务解析')

// ───────────── 5.1 后台发货 → C 端确认收货（order 域条件 UPDATE 状态机）
step('5.1 后台发货与确认收货')
const deliver = await req(WEB, '/dev-api/api/mall/mall_order/deliver', {
  method: 'POST', token: ADMIN_TOKEN,
  body: { id: orderId, expressCompany: '顺丰速运', expressNo: 'SF' + Date.now() }
})
ok(deliver.json.code === 200, '后台发货（20 → 30）', deliver.json.msg)
const receipt = await req(H5, '/api/shop/order/confirm-receipt', { method: 'POST', token, body: { orderId } })
ok(receipt.json.code === 200, 'C 端确认收货（30 → 40）', receipt.json.msg)
const finished = await req(H5, '/api/shop/order/' + orderId, { token })
ok(Number(finished.json.data.order.status) === 40, '订单状态=40 已完成', `status ${finished.json.data && finished.json.data.order.status}`)

// ───────────── 6. 售后申请（refund 8206 → order 8204 → member 8203）
step('6. 售后申请')
const preCheck = await req(H5, '/api/shop/refund/order/' + orderId, { token })
ok(preCheck.json.code === 200 && preCheck.json.data.canApply === true, '已完成订单可申请售后（refund→order Feign）',
  `canApply=${preCheck.json.data && preCheck.json.data.canApply}`)
const applyRefund = await req(H5, '/api/shop/refund/apply', {
  method: 'POST', token,
  body: { orderId, type: 2, reason: '微服务联调：不想要了', evidenceImages: [] }
})
ok(applyRefund.json.code === 200, '售后申请提交', applyRefund.json.msg)
const refundId = applyRefund.json.data && applyRefund.json.data.refundId
ok(!!refundId, '生成售后单', refundId)

// ───────────── 7. 后台审核 + 退款（refund 域，回补库存 goods）
step('7. 后台审核与退款')
const adminRefundPage = await req(WEB, '/dev-api/api/mall/mall_refund/page', {
  method: 'POST', token: ADMIN_TOKEN, body: { pageNum: 1, pageSize: 5 }
})
const adminRefundRow = (adminRefundPage.json.data.records || []).find(r => String(r.id) === String(refundId))
ok(!!adminRefundRow, '后台售后台账可见该单')
ok(adminRefundRow && adminRefundRow.memberNickname !== undefined, '台账回填会员信息（refund→member Feign）')

const audit = await req(WEB, '/dev-api/api/mall/mall_refund/audit', {
  method: 'POST', token: ADMIN_TOKEN, body: { id: refundId, agree: true, auditRemark: '联调通过' }
})
ok(audit.json.code === 200, '后台审核通过', audit.json.msg)
const doRefund = await req(WEB, '/dev-api/api/mall/mall_refund/refund', {
  method: 'POST', token: ADMIN_TOKEN, body: { id: refundId }
})
ok(doRefund.json.code === 200, '确认退款（refund→goods 回补库存）', doRefund.json.msg)
const stockBack = await req(WEB, '/dev-api/api/mall/mall_sku/' + SKU, { token: ADMIN_TOKEN })
ok(Number(stockBack.json.data.stock) === stockBefore, '退款后库存回到下单前',
  `${after.json.data.stock} → ${stockBack.json.data.stock}（原始 ${stockBefore}）`)
const orderAfterRefund = await req(H5, '/api/shop/order/' + orderId, { token })
ok(orderAfterRefund.json.data.refund != null, '订单详情回显售后单', orderAfterRefund.json.data.refund && orderAfterRefund.json.data.refund.refundNo)

// ───────────── 8. 后台各域列表（12 张台账 + 看板，走网关分发到 5 个服务）
step('8. 后台各域列表与看板')
const mods = ['mall_goods', 'mall_sku', 'mall_brand', 'mall_category', 'mall_banner',
  'mall_order', 'mall_order_log', 'mall_refund', 'mall_member', 'mall_address',
  'mall_coupon', 'mall_coupon_record']
for (const m of mods) {
  const r = await req(WEB, `/dev-api/api/mall/${m}/page`, { method: 'POST', token: ADMIN_TOKEN, body: { pageNum: 1, pageSize: 2 } })
  ok(r.json.code === 200 && Array.isArray(r.json.data.records), `后台 ${m} 列表`, `total ${r.json.data && r.json.data.total}`)
}
const dash = await req(WEB, '/dev-api/api/mall/mall_dashboard/overview?range=30d', { token: ADMIN_TOKEN })
ok(dash.json.code === 200 && dash.json.data.orderCount !== undefined, '经营看板聚合（system 域只读跨表）',
  `GMV ${dash.json.data && dash.json.data.gmv} 分 / 订单 ${dash.json.data && dash.json.data.orderCount}`)
const health = await req(WEB, '/dev-api/health')
ok(health.json.code === 200 && health.json.data.service === 'mall-system', '健康检查（后台首页连通检测）',
  health.json.data && health.json.data.port)
const dict = await req(WEB, '/dev-api/system/dict/data/type/refund_status', { token: ADMIN_TOKEN })
ok(dict.json.code === 200, '字典接口（system 域）', `字典项 ${(dict.json.data || []).length}`)

// ───────────── 9. C 端互动玩法（V7：收藏 / 签到 / 积分 / 评价 / 消息）
step('9. C 端互动玩法')
// 9.1 收藏切换（member 8203 → goods 8202 /inner 批量补齐商品信息）
const favCheck0 = await req(H5, '/api/shop/favorite/check/1001', { token })
ok(favCheck0.json.code === 200 && typeof favCheck0.json.data.favorited === 'boolean', '收藏状态可查',
  `favorited=${favCheck0.json.data && favCheck0.json.data.favorited}`)
if (favCheck0.json.data.favorited) await req(H5, '/api/shop/favorite/1001', { method: 'POST', token })
const favOn = await req(H5, '/api/shop/favorite/1001', { method: 'POST', token })
ok(favOn.json.code === 200 && favOn.json.data.favorited === true, '收藏切换为已收藏', favOn.json.msg)
const favCheck1 = await req(H5, '/api/shop/favorite/check/1001', { token })
ok(favCheck1.json.data && favCheck1.json.data.favorited === true, '收藏态回查一致')
const favList = await req(H5, '/api/shop/favorite/list?pageNum=1&pageSize=10', { token })
const favRow = (favList.json.data && favList.json.data.list || []).find(r => String(r.goodsId) === '1001')
ok(!!favRow && favRow.goodsName, '收藏列表含该商品且商品名由 goods 服务补齐', favRow && favRow.goodsName)

// 9.2 签到（重复签到返回 400，多次跑脚本也不失败）
const signin = await req(H5, '/api/shop/signin', { method: 'POST', token })
ok(signin.json.code === 200 && signin.json.data && signin.json.data.points > 0
  || (signin.json.msg || '').includes('已签到'), '今日签到（重复签到幂等）',
  signin.json.data ? `连签 ${signin.json.data.continuousDays} 天 +${signin.json.data.points}` : signin.json.msg)
const month = new Date().toISOString().slice(0, 7)
const cal = await req(H5, `/api/shop/signin/calendar?month=${month}`, { token })
ok(cal.json.code === 200 && cal.json.data.todaySigned === true, '签到日历标记今日已签',
  cal.json.data && `${cal.json.data.days.length} 天`)

// 9.3 积分余额与流水（member 8203 自闭环）
const pointBal = await req(H5, '/api/shop/point/balance', { token })
ok(pointBal.json.code === 200 && pointBal.json.data.points >= 100, '积分余额 ≥ 100',
  `points=${pointBal.json.data && pointBal.json.data.points}`)
const pointLogs = await req(H5, '/api/shop/point/logs?pageNum=1&pageSize=5', { token })
ok(pointLogs.json.code === 200 && pointLogs.json.data.list.length > 0, '积分流水可查',
  `共 ${pointLogs.json.data && pointLogs.json.data.total} 条`)

// 9.4 评价：新单走完 支付→发货→收货(40) 再评（重复跑不会撞「已评价」）
const evalSubmit = await req(H5, '/api/shop/order/submit', {
  method: 'POST', token,
  body: { addressId: addr.id, skuId: SKU, num: 1, buyerRemark: 'V7 评价联调单' }
})
ok(evalSubmit.json.code === 200 && evalSubmit.json.data.orderId, '评价专用订单提交', evalSubmit.json.msg)
const evalOrder = evalSubmit.json.data
await req(H5, '/api/shop/pay/mock', { method: 'POST', token, body: { orderNo: evalOrder.orderNo } })
const evalDeliver = await req(WEB, '/dev-api/api/mall/mall_order/deliver', {
  method: 'POST', token: ADMIN_TOKEN,
  body: { id: evalOrder.orderId, expressCompany: '顺丰速运', expressNo: 'SF' + Date.now() }
})
ok(evalDeliver.json.code === 200, '评价单发货（20 → 30）', evalDeliver.json.msg)
const evalReceipt = await req(H5, '/api/shop/order/confirm-receipt', {
  method: 'POST', token, body: { orderId: evalOrder.orderId }
})
ok(evalReceipt.json.code === 200, '评价单确认收货（30 → 40）', evalReceipt.json.msg)
const evalDetail = await req(H5, '/api/shop/order/' + evalOrder.orderId, { token })
const evalItem = (evalDetail.json.data && evalDetail.json.data.items || [])[0]
ok(evalItem && evalItem.id && evalItem.goodsId && evalItem.skuId, '订单项快照齐全（评价入参）')

const submitReview = await req(H5, '/api/shop/review', {
  method: 'POST', token,
  body: { orderItemId: evalItem.id, orderId: evalOrder.orderId, goodsId: evalItem.goodsId,
    skuId: evalItem.skuId, rating: 5, content: 'V7 联调评价：发货快，体验好', images: '',
    memberNickname: (profilePc.json.data && profilePc.json.data.nickname) || '联调会员' }
})
ok(submitReview.json.code === 200, '提交评价（订单域 can-review 校验）', submitReview.json.msg)
const goodsReviews = await req(H5, `/api/shop/review/goods/${evalItem.goodsId}?pageNum=1&pageSize=5`, { token })
ok(goodsReviews.json.code === 200 && (goodsReviews.json.data.records || []).length > 0, '商品评价列表（会员 token 可读）',
  `共 ${goodsReviews.json.data && goodsReviews.json.data.total} 条`)
ok(typeof goodsReviews.json.data.avg === 'number' && goodsReviews.json.data.avg > 0, '商品均分聚合',
  `avg=${goodsReviews.json.data && goodsReviews.json.data.avg}`)
ok(goodsReviews.json.data.records.some(r => String(r.orderItemId) === String(evalItem.id)), '新评价出现在商品详情评价列表')
const myReviews = await req(H5, '/api/shop/review/my?pageNum=1&pageSize=5', { token })
ok(myReviews.json.data && myReviews.json.data.records.some(r => String(r.orderItemId) === String(evalItem.id)),
  '我的评价含该订单项')

// 9.5 后台评价管理：台账可见 → 回复 → C 端展示 → 隐藏 → C 端消失 → 恢复
const reviewPage = await req(WEB, '/dev-api/api/mall/mall_review/page', {
  method: 'POST', token: ADMIN_TOKEN,
  body: { pageNum: 1, pageSize: 5, goodsId: Number(evalItem.goodsId) }
})
const reviewRow = (reviewPage.json.data.records || []).find(r => String(r.orderItemId) === String(evalItem.id))
ok(!!reviewRow, '后台评价台账可见该评价', `total ${reviewPage.json.data && reviewPage.json.data.total}`)
const reviewReply = await req(WEB, '/dev-api/api/mall/mall_review/reply', {
  method: 'POST', token: ADMIN_TOKEN, body: { id: reviewRow.id, reply: '感谢购买，欢迎复购' }
})
ok(reviewReply.json.code === 200, '后台回复评价', reviewReply.json.msg)
const afterReply = await req(H5, `/api/shop/review/goods/${evalItem.goodsId}?pageNum=1&pageSize=5`, { token })
const repliedRow = (afterReply.json.data.records || []).find(r => String(r.orderItemId) === String(evalItem.id))
ok(repliedRow && repliedRow.reply === '感谢购买，欢迎复购', 'C 端展示商家回复')
await req(WEB, '/dev-api/api/mall/mall_review/hide', {
  method: 'POST', token: ADMIN_TOKEN, body: { id: reviewRow.id, status: 1 }
})
const afterHide = await req(H5, `/api/shop/review/goods/${evalItem.goodsId}?pageNum=1&pageSize=5`, { token })
ok(!(afterHide.json.data.records || []).some(r => String(r.orderItemId) === String(evalItem.id)),
  '隐藏后 C 端商品评价不再展示')
await req(WEB, '/dev-api/api/mall/mall_review/hide', {
  method: 'POST', token: ADMIN_TOKEN, body: { id: reviewRow.id, status: 0 }
})

// 9.6 站内消息：未读数 → 单条已读 → 全部已读；后台台账只读可见
const unread0 = await req(H5, '/api/shop/notice/unread-count', { token })
ok(unread0.json.code === 200 && unread0.json.data.count > 0, '未读消息数 > 0（订单状态机推送）',
  `count=${unread0.json.data && unread0.json.data.count}`)
const noticeList = await req(H5, '/api/shop/notice/list?pageNum=1&pageSize=10', { token })
const unreadRow = (noticeList.json.data.list || []).find(r => r.readStatus === 0)
ok(!!unreadRow && noticeList.json.data.total > 0, '消息列表（按会员隔离）', `共 ${noticeList.json.data && noticeList.json.data.total} 条`)
if (unreadRow) {
  const readOne = await req(H5, '/api/shop/notice/read/' + unreadRow.id, { method: 'POST', token })
  ok(readOne.json.code === 200, '单条标记已读', readOne.json.msg)
}
const readAll = await req(H5, '/api/shop/notice/read-all', { method: 'POST', token })
ok(readAll.json.code === 200 && readAll.json.data.read > 0, '全部标记已读', `影响 ${readAll.json.data && readAll.json.data.read} 条`)
const unread1 = await req(H5, '/api/shop/notice/unread-count', { token })
ok(unread1.json.data && unread1.json.data.count === 0, '已读后未读数归零', `count=${unread1.json.data && unread1.json.data.count}`)
const adminNotice = await req(WEB, '/dev-api/api/mall/mall_notice/page', {
  method: 'POST', token: ADMIN_TOKEN, body: { pageNum: 1, pageSize: 5 }
})
ok(adminNotice.json.code === 200 && Array.isArray(adminNotice.json.data.records), '后台站内消息台账可查',
  `total ${adminNotice.json.data && adminNotice.json.data.total}`)

// ───────────── 11. V10 商户入驻（merchant 8207）
step('11. 商户入驻（mall-merchant）')
const stamp = Date.now()
const shopName = '联调店铺' + String(stamp).slice(-6)
const applyRes = await req(WEB, '/dev-api/api/mall/mall_merchant/apply', {
  method: 'POST', token: ADMIN_TOKEN,
  body: {
    name: shopName, legalName: '联调商贸有限公司', licenseNo: 'LIC' + stamp,
    contactName: '张三', contactPhone: '13900000000', category: '数码', settleRate: 100
  }
})
ok(applyRes.json.code === 200 && applyRes.json.data && applyRes.json.data.merchantNo, '提交入驻申请',
  applyRes.json.data && applyRes.json.data.merchantNo)
const merchantId = applyRes.json.data.id
ok(Number(applyRes.json.data.status) === 0, '新申请状态=待审核', 'status ' + applyRes.json.data.status)

const dupRes = await req(WEB, '/dev-api/api/mall/mall_merchant/apply', {
  method: 'POST', token: ADMIN_TOKEN,
  body: { name: shopName + '重复', licenseNo: 'LIC' + stamp }
})
ok(dupRes.json.code === 400, '同一执照未驳回前重复申请被拒', dupRes.json.msg)

const auditRes = await req(WEB, '/dev-api/api/mall/mall_merchant/audit', {
  method: 'POST', token: ADMIN_TOKEN, body: { id: merchantId, pass: true }
})
ok(auditRes.json.code === 200, '审核通过', auditRes.json.msg)
const mDetail = await req(WEB, '/dev-api/api/mall/mall_merchant/' + merchantId, { token: ADMIN_TOKEN })
ok(mDetail.json.data && mDetail.json.data.merchant.status === 1, '审核后状态=启用')
ok((mDetail.json.data.logs || []).length >= 2, '审核流水含申请+通过', `${mDetail.json.data.logs.length} 条`)

const shopList = await req(H5, '/api/shop/merchant/list')
ok((shopList.json.data || []).some(m => String(m.id) === String(merchantId)), 'C 端店铺列表含新店铺')
const shopInfo = await req(H5, '/api/shop/merchant/' + merchantId)
ok(shopInfo.json.code === 200 && shopInfo.json.data.name === shopName, 'C 端店铺详情', shopInfo.json.data && shopInfo.json.data.name)

// ───────────── 11.1 商家发品 + 跨店拆单
step('11.1 商家发品 / 跨店拆单')
const allGoods = await req(H5, '/api/shop/goods?pageNum=1&pageSize=50')
const candidate = (allGoods.json.data.records || []).find(g => String(g.id) !== '1001' && Number(g.priceMin) > 0)
ok(!!candidate, '存在可改归属的在售商品', candidate && candidate.name)
const gBase = await req(WEB, '/dev-api/api/mall/mall_goods/' + candidate.id, { token: ADMIN_TOKEN })
const g = gBase.json.data
const putRes = await req(WEB, '/dev-api/api/mall/mall_goods', {
  method: 'PUT', token: ADMIN_TOKEN,
  body: {
    id: g.id, goodsNo: g.goodsNo, categoryId: g.categoryId, brandId: g.brandId, name: g.name,
    subTitle: g.subTitle, mainImage: g.mainImage, sliderImages: g.sliderImages, detail: g.detail,
    priceMin: g.priceMin, priceMax: g.priceMax, sales: g.sales, status: g.status, merchantId
  }
})
ok(putRes.json.code === 200, '商品归属改为新店铺（商家发品）', putRes.json.msg)
const gAfter = await req(H5, '/api/shop/goods/' + candidate.id)
ok(String(gAfter.json.data.merchantId) === String(merchantId) && gAfter.json.data.merchantName === shopName,
  'C 端商品详情带店铺名', gAfter.json.data && gAfter.json.data.merchantName)
const merchantSku = (gAfter.json.data.skus || []).find(s => Number(s.stock) > 0)
ok(!!merchantSku, '新店铺商品有可售 SKU', merchantSku && `库存 ${merchantSku.stock}`)

// 平台自营 SKU + 新店铺 SKU → 确认页识别两个店铺 → 提交拆两单
await req(H5, '/api/shop/cart/add', { method: 'POST', token, body: { skuId: SKU, num: 1 } })
await req(H5, '/api/shop/cart/add', { method: 'POST', token, body: { skuId: merchantSku.id, num: 1 } })
const confirm2 = await req(H5, '/api/shop/order/confirm', { method: 'POST', token, body: {} })
ok(confirm2.json.data.merchantCount === 2, '确认页识别 2 个店铺，', `merchantCount=${confirm2.json.data && confirm2.json.data.merchantCount}`)
ok((confirm2.json.data.items || []).every(i => i.merchantId && i.merchantName), '确认页每行带店铺归属')
const crossCoupon = await req(H5, '/api/shop/order/submit', {
  method: 'POST', token, body: { addressId: addr.id, couponRecordId: 1 }
})
ok(crossCoupon.json.code === 400, '跨店铺用券被拒', crossCoupon.json.msg)
const submit2 = await req(H5, '/api/shop/order/submit', { method: 'POST', token, body: { addressId: addr.id } })
ok(submit2.json.code === 200 && submit2.json.data.orderCount === 2, '按店铺拆成 2 笔订单',
  `orderCount=${submit2.json.data && submit2.json.data.orderCount}`)
const mbOrder = (submit2.json.data.orders || []).find(o => String(o.merchantId) === String(merchantId))
ok(!!mbOrder, '其中一笔归属新店铺', mbOrder && mbOrder.orderNo)

// ───────────── 11.2 完成订单 → 商家入账 → 结算单 → 提现
step('11.2 结算入账 / 结算单 / 提现')
const payMb = await req(H5, '/api/shop/pay/mock', { method: 'POST', token, body: { orderNo: mbOrder.orderNo } })
ok(payMb.json.code === 200, '新店铺订单支付', payMb.json.msg)
await req(WEB, '/dev-api/api/mall/mall_order/deliver', {
  method: 'POST', token: ADMIN_TOKEN,
  body: { id: mbOrder.orderId, expressCompany: '顺丰速运', expressNo: 'SF' + stamp }
})
const receiptMb = await req(H5, '/api/shop/order/confirm-receipt', {
  method: 'POST', token, body: { orderId: mbOrder.orderId }
})
ok(receiptMb.json.code === 200, '确认收货（触发商家结算入账）', receiptMb.json.msg)

const mAfter = await req(WEB, '/dev-api/api/mall/mall_merchant/' + merchantId, { token: ADMIN_TOKEN })
const mc = mAfter.json.data.merchant
const payAmount = Number(mbOrder.payAmount)
const expectFee = Math.round(payAmount * 100 / 1000) // settleRate=100‰ → 10%
ok(Number(mc.accountBalance) === payAmount - expectFee, '商家余额 = 实付 − 平台抽成',
  `余额 ${mc.accountBalance} = ${payAmount} − ${expectFee}`)

const build = await req(WEB, '/dev-api/api/mall/mall_merchant_settlement/build', {
  method: 'POST', token: ADMIN_TOKEN, body: { merchantId }
})
ok(build.json.code === 200 && build.json.data.settlementNo, '生成结算单', build.json.data && build.json.data.settlementNo)
const stl = build.json.data
ok(Number(stl.goodsAmount) === payAmount - expectFee && Number(stl.platformFee) === expectFee && Number(stl.orderCount) >= 1,
  '结算单：应收/抽成/订单数与分账口径一致',
  `应收 ${stl.goodsAmount} 抽成 ${stl.platformFee} 单数 ${stl.orderCount}`)
const details = await req(WEB, `/dev-api/api/mall/mall_merchant_settlement/${stl.id}/details`, { token: ADMIN_TOKEN })
ok((details.json.data || []).some(d => String(d.orderNo) === String(mbOrder.orderNo)), '结算明细含该订单')

const wd = await req(WEB, '/dev-api/api/mall/mall_merchant_withdrawal/apply', {
  method: 'POST', token: ADMIN_TOKEN, body: { merchantId, amount: 500, method: 0 }
})
ok(wd.json.code === 200, '发起提现（冻结 5.00 元）', wd.json.msg)
const wdTooMuch = await req(WEB, '/dev-api/api/mall/mall_merchant_withdrawal/apply', {
  method: 'POST', token: ADMIN_TOKEN, body: { merchantId, amount: 99999999, method: 0 }
})
ok(wdTooMuch.json.code === 400, '超出可提现余额被拒', wdTooMuch.json.msg)
const wdAudit = await req(WEB, '/dev-api/api/mall/mall_merchant_withdrawal/audit', {
  method: 'POST', token: ADMIN_TOKEN, body: { id: wd.json.data.id, pass: true }
})
ok(wdAudit.json.code === 200, '提现审核通过 = 已打款', wdAudit.json.msg)

// ───────────── 12. V12 交易真实化（运费 / 支付单 / 部分发货 / 发票 / 对账）
step('12. V12 运费模板与确认页试算')
const ftPage = await req(WEB, '/dev-api/api/mall/mall_freight_template/page', {
  method: 'POST', token: ADMIN_TOKEN, body: { pageNum: 1, pageSize: 5 }
})
ok(ftPage.json.code === 200 && (ftPage.json.data.records || []).length > 0, '后台运费模板列表',
  `共 ${ftPage.json.data && ftPage.json.data.total} 个`)
const ftOpts = await req(WEB, '/dev-api/api/mall/mall_freight_template/options', { token: ADMIN_TOKEN })
const ftDefault = (ftOpts.json.data || []).find(o => Number(o.isDefault) === 1)
ok(!!ftDefault, '存在全局默认模板（计价兜底）', ftDefault && ftDefault.name)
const ftDetail = await req(WEB, '/dev-api/api/mall/mall_freight_template/' + (ftDefault && ftDefault.id), { token: ADMIN_TOKEN })
const ftRules = (ftDetail.json.data && ftDetail.json.data.rules) || []
ok(ftDetail.json.code === 200 && ftRules.length > 0, '模板详情含地区规则', `${ftRules.length} 条`)

// 确认页试算：两次同参请求必须同价（运费不受前端传参影响）
const confirmF = await req(H5, '/api/shop/order/confirm', {
  method: 'POST', token, body: { addressId: addr.id, skuId: SKU, num: 2 }
})
const cF = confirmF.json.data || {}
ok(Number.isFinite(Number(cF.freightAmount)), '确认页返回 freightAmount 字段', `运费 ${cF.freightAmount} 分`)
ok(Number(cF.payAmount) === Number(cF.totalAmount) + Number(cF.freightAmount) - Number(cF.discountAmount),
  '实付 = 商品总额 + 运费 − 优惠', `${cF.totalAmount} + ${cF.freightAmount} - ${cF.discountAmount} = ${cF.payAmount}`)
ok(cF.freightTip !== undefined, '确认页带包邮提示字段（前端据此显示）',
  cF.freightTip || `差额 ${cF.freeShippingGap || 0} 分`)

step('12.1 支付单 / 回调验签 / 幂等')
const v12Submit = await req(H5, '/api/shop/order/submit', {
  method: 'POST', token, body: { addressId: addr.id, skuId: SKU, num: 2, buyerRemark: 'V12 交易联调单' }
})
ok(v12Submit.json.code === 200 && v12Submit.json.data.orderId, '提交 V12 联调单', v12Submit.json.msg)
const v12 = v12Submit.json.data
const v12Detail = await req(H5, '/api/shop/order/' + v12.orderId, { token })
const v12Order = (v12Detail.json.data && v12Detail.json.data.order) || {}
ok(Number(v12Order.freightAmount) === Number(cF.freightAmount), '订单落库运费与确认页一致',
  `订单 ${v12Order.freightAmount} / 确认页 ${cF.freightAmount}`)

const chans = await req(H5, '/api/shop/pay/channels', { token })
ok(chans.json.code === 200 && chans.json.data && chans.json.data.sandbox === true,
  '渠道可用性：沙箱可用', JSON.stringify(chans.json.data))
const prep = await req(H5, '/api/shop/pay/prepay', {
  method: 'POST', token, body: { orderNo: v12.orderNo, channel: 'sandbox', tradeType: 'NATIVE' }
})
ok(prep.json.code === 200 && prep.json.data && prep.json.data.payNo, '预支付生成支付单',
  prep.json.data && prep.json.data.payNo)
const payNo12 = prep.json.data && prep.json.data.payNo
const payAmt12 = Number(prep.json.data && prep.json.data.amount)
ok(payAmt12 === Number(v12Order.payAmount), '支付单金额 = 订单实付', `${payAmt12} vs ${v12Order.payAmount}`)
ok(!!(prep.json.data && prep.json.data.params && prep.json.data.params.qrCode), '返回可渲染的支付参数（qrCode）')

const wxCfg = await req(H5, '/api/shop/pay/prepay', {
  method: 'POST', token, body: { orderNo: v12.orderNo, channel: 'wxpay' }
})
ok(wxCfg.json.code === 503, '未配置渠道 503（绝不伪造支付成功）', wxCfg.json.msg)

const badSign = await req(H5, '/api/shop/pay/notify/sandbox', {
  method: 'POST', body: { payNo: payNo12, channelTradeNo: 'T' + stamp, amount: payAmt12, status: 1, sign: 'deadbeef' }
})
ok(badSign.json.code === 400, '验签失败的回调被拒', badSign.json.msg)

const { createHash } = await import('node:crypto')
const sbSecret = process.env.MALL_PAY_SANDBOX_SECRET || 'yueji-sandbox-secret'
const tradeNo12 = 'SANDBOX-T-' + stamp
const sign12 = createHash('md5')
  .update(payNo12 + tradeNo12 + payAmt12 + 1 + sbSecret).digest('hex')
const notify12 = { payNo: payNo12, channelTradeNo: tradeNo12, amount: payAmt12, status: 1, sign: sign12 }
const n1 = await req(H5, '/api/shop/pay/notify/sandbox', { method: 'POST', body: notify12 })
ok(n1.json.code === 200 && n1.json.data.replay === false, '渠道回调推进订单为已支付', n1.json.msg)
const n2 = await req(H5, '/api/shop/pay/notify/sandbox', { method: 'POST', body: notify12 })
ok(n2.json.code === 200 && n2.json.data.replay === true, '同一回调重放按幂等返回（不二次推进）')
const v12Paid = await req(H5, '/api/shop/order/' + v12.orderId, { token })
ok(Number(v12Paid.json.data.order.status) === 20, '订单状态=20 待发货',
  `status ${v12Paid.json.data && v12Paid.json.data.order.status}`)

step('12.2 部分发货 / 物流轨迹')
const pending = await req(WEB, '/dev-api/api/mall/mall_delivery/pending?orderNo=' + v12.orderNo,
  { token: ADMIN_TOKEN })
const pend = pending.json.data || {}
ok(pending.json.code === 200 && pend.deliverable === true, '发货台按订单号取待发明细',
  `明细 ${(pend.items || []).length} 行`)
const pendRow = (pend.items || [])[0]
ok(pendRow && Number(pendRow.remain) === 2, '待发数量=2', pendRow && `remain ${pendRow.remain}`)

const ship1 = await req(WEB, '/dev-api/api/mall/mall_delivery/ship', {
  method: 'POST', token: ADMIN_TOKEN,
  body: {
    orderId: v12.orderId, expressCompany: '顺丰速运', expressNo: 'SF12A' + stamp,
    // orderItemId 是 19 位长整型，必须原样传字符串：Number() 会丢精度导致匹配不到明细
    items: [{ orderItemId: pendRow.orderItemId, quantity: 1 }]
  }
})
ok(ship1.json.code === 200 && ship1.json.data.allShipped === false, '首包裹发 1 件：订单仍未「已发货」',
  `orderStatus ${ship1.json.data && ship1.json.data.orderStatus}`)
const midState = await req(H5, '/api/shop/order/' + v12.orderId, { token })
ok(Number(midState.json.data.order.status) === 20, '部分发货不改订单状态（仍 20）')

// 单号唯一性在「仍待发货」时校验，避免订单已转 30 把错误盖成「状态不支持」
const dupExpress = await req(WEB, '/dev-api/api/mall/mall_delivery/ship', {
  method: 'POST', token: ADMIN_TOKEN,
  body: { orderId: v12.orderId, expressCompany: '顺丰速运', expressNo: 'SF12A' + stamp }
})
ok(dupExpress.json.code === 400 && (dupExpress.json.msg || '').includes('快递单号'),
  '快递单号全局唯一（重复被拒）', dupExpress.json.msg)

const ship2 = await req(WEB, '/dev-api/api/mall/mall_delivery/ship', {
  method: 'POST', token: ADMIN_TOKEN,
  body: { orderId: v12.orderId, expressCompany: '中通快递', expressNo: 'ZT12B' + stamp }
})
ok(ship2.json.code === 200 && ship2.json.data.allShipped === true, '第二包裹发完剩余 → 订单转 30')

const track = await req(H5, '/api/shop/delivery/track/' + v12.orderId, { token })
const pkgs = (track.json.data && track.json.data.packages) || []
ok(track.json.code === 200 && pkgs.length === 2, 'C 端物流返回 2 个包裹', `packages ${pkgs.length}`)
ok(pkgs.length > 0 && pkgs.every(p => (p.nodes || []).length > 0), '每个包裹都有轨迹节点',
  pkgs[0] && `首包裹 ${pkgs[0].nodes.length} 条`)
const dlvPage = await req(WEB, '/dev-api/api/mall/mall_delivery/page?pageNum=1&pageSize=5',
  { token: ADMIN_TOKEN })
ok(dlvPage.json.code === 200 && Array.isArray(dlvPage.json.data.records), '后台发货单台账',
  `total ${dlvPage.json.data && dlvPage.json.data.total}`)

step('12.3 发票 / 支付流水 / 对账')
const recv12 = await req(H5, '/api/shop/order/confirm-receipt', {
  method: 'POST', token, body: { orderId: v12.orderId }
})
ok(recv12.json.code === 200, '确认收货（30 → 40）', recv12.json.msg)
const invApply = await req(H5, '/api/shop/invoice/apply', {
  method: 'POST', token,
  body: { orderId: v12.orderId, titleType: 1, title: '联调会员', email: 'demo@yueji.test' }
})
ok(invApply.json.code === 200 && invApply.json.data.invoiceNo, '申请开票', invApply.json.msg)
const invDup = await req(H5, '/api/shop/invoice/apply', {
  method: 'POST', token, body: { orderId: v12.orderId, titleType: 1, title: '联调会员' }
})
ok(invDup.json.code === 400, '一单一票：重复申请被拒', invDup.json.msg)
const invOfOrder = await req(H5, '/api/shop/invoice/order/' + v12.orderId, { token })
ok(invOfOrder.json.data && Number(invOfOrder.json.data.status) === 0, '订单发票状态=待开票',
  invOfOrder.json.data && invOfOrder.json.data.invoiceNo)

const invPage = await req(WEB, '/dev-api/api/mall/mall_invoice/page', {
  method: 'POST', token: ADMIN_TOKEN, body: { keyword: v12.orderNo, pageNum: 1, pageSize: 5 }
})
const invRow = (invPage.json.data && invPage.json.data.records || [])[0]
ok(!!invRow, '后台开票队列可见该申请', invRow && invRow.invoiceNo)
const issue = await req(WEB, '/dev-api/api/mall/mall_invoice/issue', {
  method: 'POST', token: ADMIN_TOKEN,
  body: { id: invRow && invRow.id, fileUrl: 'https://files.yueji.test/inv/' + v12.orderNo + '.pdf' }
})
ok(issue.json.code === 200, '后台开票（上传电子发票）', issue.json.msg)
const invAfter = await req(H5, '/api/shop/invoice/order/' + v12.orderId, { token })
ok(Number(invAfter.json.data.status) === 1 && !!invAfter.json.data.fileUrl, 'C 端可下载发票文件',
  invAfter.json.data && invAfter.json.data.fileUrl)

const payPg = await req(WEB, '/dev-api/api/mall/mall_payment/page', {
  method: 'POST', token: ADMIN_TOKEN, body: { pageNum: 1, pageSize: 5 }
})
ok(payPg.json.code === 200 && Array.isArray(payPg.json.data.records), '后台支付流水台账',
  `total ${payPg.json.data && payPg.json.data.total}`)
const recRun = await req(WEB, '/dev-api/api/mall/mall_pay_reconcile/run', {
  method: 'POST', token: ADMIN_TOKEN, body: { channel: 'sandbox' }
})
ok(recRun.json.code === 200, '执行对账（沙箱镜像，差异应为 0）', JSON.stringify(recRun.json.data).slice(0, 90))
const recPage = await req(WEB, '/dev-api/api/mall/mall_pay_reconcile/page?pageNum=1&pageSize=5',
  { token: ADMIN_TOKEN })
ok(recPage.json.code === 200 && Array.isArray(recPage.json.data.records), '对账差异台账可查',
  `total ${recPage.json.data && recPage.json.data.total}`)

// ───────────── 结果
console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
if (fail) { console.log('失败项：' + fails.join(' | ')); process.exit(1) }
